"""Presentation for explicitly scoped, synthetic iCalendar occupied-time analysis."""

import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import pandas as pd
from matplotlib.ticker import MaxNLocator
from matplotlib.patches import Patch
from IPython.display import display
from vgi_notebooks import finish, prose

SOURCE = "Source: authored synthetic-day.ics · native VGI iCal v0.4.1 · fixed UTC window · recurrence not expanded"


def validate_buffer(value):
    if type(value) is not int or not 0 <= value <= 60:
        raise ValueError("BUFFER_MINUTES must be an integer from 0 through 60.")


def sample_finding(samples):
    recurring = samples.loc[samples.rule_text.notna()]
    prose(
        f"The **{len(samples)} built-in feeds** declare **{int(samples.declared_components.sum())} VEVENT components**, "
        f"and the parser returns **{int(samples.parsed_components.sum())}**. "
        f"**{len(recurring)} feed** carries an RRULE while contributing just "
        f"**{int(recurring.parsed_components.sum())} parsed component**. "
        "Those counts describe stored entries; they do not count expanded occurrences or occupied minutes. "
        "The controls stay separate from the selected schedule below."
    )


def timeline(schedule, blocks, BUFFER_MINUTES):
    fig, ax = plt.subplots(figsize=(11, 6))
    for y, row in enumerate(schedule.itertuples()):
        for start, end, color, height in [
            (row.reserved_start, row.reserved_end, "#cfdbeb", 0.56),
            (row.start_utc, row.end_utc, "#267358", 0.30),
        ]:
            ax.barh(
                y,
                mdates.date2num(end) - mdates.date2num(start),
                left=mdates.date2num(start),
                height=height,
                color=color,
            )
    union_y = len(schedule) + 0.4
    for row in blocks.itertuples():
        ax.barh(
            union_y,
            mdates.date2num(row.block_end) - mdates.date2num(row.block_start),
            left=mdates.date2num(row.block_start),
            height=0.45,
            color="#456fba",
        )
    ax.axhline(len(schedule) - 0.3, color="#cbd5cf", lw=1)
    ax.set_yticks(
        list(range(len(schedule))) + [union_y], schedule.summary.tolist() + ["Occupied union"]
    )
    ax.invert_yaxis()
    ax.set_xlim(
        mdates.date2num(pd.Timestamp("2026-01-05 08:00")),
        mdates.date2num(pd.Timestamp("2026-01-05 17:00")),
    )
    ax.xaxis.set_major_locator(mdates.HourLocator())
    ax.xaxis.set_major_formatter(mdates.DateFormatter("%H:%M"))
    ax.set_xlabel("UTC on 5 January 2026")
    ax.grid(axis="y", visible=False)
    fig.legend(
        handles=[
            Patch(color="#267358", label="Original event"),
            Patch(color="#cfdbeb", label="Reserved with buffer"),
            Patch(color="#456fba", label="Occupied union"),
        ],
        loc="lower center",
        bbox_to_anchor=(0.55, 0.78),
        ncol=3,
        frameon=False,
        fontsize=10,
    )
    finish(
        fig,
        "Count covered time once",
        f"{len(schedule)} selected timed events · {BUFFER_MINUTES}-minute buffer on each side",
        SOURCE,
        name="01-occupied-timeline",
    )
    prose(
        f"The **{len(schedule)} selected events** form **{len(blocks)} occupied block(s)** after buffering. "
        "Their original times are unchanged. The union describes only the selected subset; "
        "the excluded recurring, all-day, unconfirmed and incomplete projections prevent a claim about complete availability."
    )


def duration_chart(metrics, BUFFER_MINUTES):
    r = metrics.iloc[0]
    duplicate = int(r.summed_minutes - r.occupied_minutes)
    values = [int(r.summed_minutes), int(r.occupied_minutes), duplicate]
    fig, ax = plt.subplots(figsize=(10, 5.5))
    bars = ax.barh(
        ["Sum of reservations", "Occupied union", "Duplicate counting"],
        values,
        color=["#456fba", "#267358", "#c57b40"],
        height=0.52,
    )
    ax.bar_label(bars, padding=5, fontsize=12)
    ax.invert_yaxis()
    ax.set_xlim(0, max(values) * 1.25)
    ax.set_xlabel("Minutes within the fixed window")
    ax.xaxis.set_major_locator(MaxNLocator(integer=True))
    ax.grid(axis="y", visible=False)
    finish(
        fig,
        "Summed duration overstates occupied time",
        f"{BUFFER_MINUTES}-minute buffer per side · Same {int(r.selected_events)} selected events",
        SOURCE,
        name="02-duration-and-union",
    )
    prose(
        f"Reservations sum to **{int(r.summed_minutes)} minutes**, but their union occupies "
        f"**{int(r.occupied_minutes)} minutes** of the {int(r.window_minutes)}-minute window. "
        f"The sum includes **{duplicate} minutes of duplicate counting**. "
        f"The strict overlap query finds **{int(r.overlapping_pairs)} event pair(s)**."
    )


def show_checks(checks):
    display(checks)
    assert checks.passed.eq(True).all(), checks.loc[~checks.passed].to_dict("records")
    prose(
        f"**{len(checks)} checks passed.** The occupied-minute calculation reconciles independently "
        "within the declared subset and window; it does not resolve the excluded event semantics."
    )
