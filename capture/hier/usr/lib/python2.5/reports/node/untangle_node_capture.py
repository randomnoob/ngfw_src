import gettext
import logging
import mx
import reports.colors as colors
import reports.i18n_helper
import reports.sql_helper as sql_helper

from psycopg2.extensions import DateFromMx
from psycopg2.extensions import QuotedString
from psycopg2.extensions import TimestampFromMx
from reports import Chart
from reports import ColumnDesc
from reports import DATE_FORMATTER
from reports import DetailSection
from reports import Graph
from reports import Highlight
from reports import HOUR_FORMATTER
from reports import KeyStatistic
from reports import PIE_CHART
from reports import Report
from reports import STACKED_BAR_CHART
from reports import SummarySection
from reports import TIME_OF_DAY_FORMATTER
from reports import TIME_SERIES_CHART
from reports.engine import Column
from reports.engine import FactTable
from reports.engine import HOST_DRILLDOWN
from reports.engine import Node
from reports.engine import TOP_LEVEL
from reports.engine import USER_DRILLDOWN
from reports.sql_helper import print_timing

_ = reports.i18n_helper.get_translation('untangle-node-capture').lgettext
def N_(message): return message

LOGIN = _('Login')
LOGOUT = _('Logout')
FAILED = _('Failed')
TIMEOUT = _('Timeout')

def auto_incr(start_value=0, amount = 1):
    v = [start_value]
    def f():
        current = v[0]
        v[0] += amount
        return current

    return f

class Capture(Node):
    def __init__(self):
        Node.__init__(self, 'untangle-node-capture')

    def setup(self):
        self.__make_n_capture_user_events_table()

        ft = FactTable('reports.n_capture_user_totals',
                       'reports.n_capture_user_events',
                       'time_stamp', [], [])
        reports.engine.register_fact_table(ft)

        ft.measures.append(Column('logins',
                                    'integer',
                                    "count(CASE WHEN event_info = 'LOGIN' THEN 1 ELSE NULL END)"))
        ft.measures.append(Column('timeouts',
                                    'integer',
                                    "count(CASE WHEN event_info = 'TIMEOUT' THEN 1 ELSE NULL END)"))
        ft.measures.append(Column('logouts',
                                    'integer',
                                    "count(CASE WHEN event_info = 'LOGOUT' THEN 1 ELSE NULL END)"))
        ft.measures.append(Column('failures',
                                  'integer',
                                  "count(CASE WHEN event_info = 'FAILED' THEN 1 ELSE NULL END)"))

        ft = reports.engine.get_fact_table('reports.session_totals')
        ft.measures.append(Column('capture_blocks', 'integer', "count(CASE WHEN capture_blocked THEN 1 ELSE null END)"))
        ft.dimensions.append(Column('capture_rule_index', 'integer'))

    def get_toc_membership(self):
        return [TOP_LEVEL]

    def parents(self):
        return ['untangle-vm']

    def get_report(self):
        sections = []

        s = SummarySection('summary', _('Summary Report'),
                           [CaptureHighlight(self.name),
                            DailyUsage(),
                            TopUsers(),
                            TopBlockedClients()])
        sections.append(s)

        sections.append(LoginDetail())
        sections.append(BlockDetail())

        return Report(self, sections)

    def reports_cleanup(self, cutoff):
        sql_helper.drop_fact_table("n_capture_user_events", cutoff)
        sql_helper.drop_fact_table("n_capture_user_totals", cutoff)

    @print_timing
    def __make_n_capture_user_events_table(self):
        sql_helper.create_fact_table("""\
CREATE TABLE reports.n_capture_user_events (
    time_stamp timestamp without time zone,
    policy_id bigint,
    login_name text,
    event_info text,
    auth_type text,
    client_addr text,
    event_id bigserial)""")

        sql_helper.add_column('reports', 'n_capture_user_events', 'event_id', 'bigserial')

        # we used to create event_id as serial instead of bigserial - convert if necessary
        sql_helper.convert_column("reports","n_capture_user_events","event_id","integer","bigint");

        sql_helper.create_index("reports","n_capture_user_events","event_id");
        sql_helper.create_index("reports","n_capture_user_events","time_stamp");

class CaptureHighlight(Highlight):
    def __init__(self, name):
        Highlight.__init__(self, name,
                           _(name) + " " +
                           _("processed") + " " + "%(logins)s" + " " +
                           _("user events"))

    @print_timing
    def get_highlights(self, end_date, report_days,
                       host=None, user=None, email=None):
        if host or user or email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT COALESCE(sum(logins), 0) as logins
FROM reports.n_capture_login_totals
WHERE trunc_time >= %s::timestamp without time zone AND trunc_time < %s::timestamp without time zone"""

        conn = sql_helper.get_connection()
        curs = conn.cursor()

        h = {}
        try:
            curs.execute(query, (one_week, ed))

            h = sql_helper.get_result_dictionary(curs)

        finally:
            conn.commit()

        return h

class DailyUsage(Graph):
    def __init__(self):
        Graph.__init__(self, 'usage', _('Usage'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email or host or user:
            return None

        start_date = end_date - mx.DateTime.DateTimeDelta(report_days)

        # dt = Day Totals.  These are the totals over the entire day.
        # pd = Per Day.  This is the number of a particular event per day.
        lks = []
        query = """
SELECT COALESCE(SUM(dt.logins_pd)/%s,0), COALESCE(MAX(dt.logins_pd),0),
       COALESCE(SUM(dt.logouts_pd)/%s,0), COALESCE(MAX(dt.logouts_pd),0),
       COALESCE(SUM(dt.timeouts_pd)/%s,0), COALESCE(MAX(dt.timeouts_pd),0),
       COALESCE(SUM(dt.failures_pd)/%s,0), COALESCE(MAX(dt.failures_pd),0),
       COALESCE(SUM(dt.logins_pd + dt.logouts_pd + dt.failures_pd)/%s,0),
       COALESCE(MAX(dt.logins_pd + dt.logouts_pd + dt.failures_pd),0)
       FROM (
           SELECT SUM(logins) AS logins_pd,
                  SUM(logouts) AS logouts_pd,
                  SUM(timeouts) AS timeouts_pd,
                  SUM(failures) AS failures_pd,
                  DATE_TRUNC('day',trunc_time) AS day
           FROM reports.n_capture_user_totals
           WHERE trunc_time >= %s::timestamp without time zone AND trunc_time < %s::timestamp without time zone
           GROUP BY day
       ) AS dt
"""
        conn = sql_helper.get_connection()
        curs = conn.cursor()
        try:
            sums = ["COALESCE(SUM(logins), 0)::float",
                    "COALESCE(SUM(logouts), 0)::float",
                    "COALESCE(SUM(timeouts), 0)::float",
                    "COALESCE(SUM(failures), 0)::float"]

            extra_where = []

            if report_days == 1:
                time_interval = 60 * 60
                unit = "Hour"
                formatter = HOUR_FORMATTER
            else:
                time_interval = 24 * 60 * 60
                unit = "Day"
                formatter = DATE_FORMATTER

            q, h = sql_helper.get_averaged_query(sums, "reports.n_capture_user_totals",
                                                 start_date,
                                                 end_date,
                                                 extra_where = extra_where,
                                                 time_interval = time_interval)
            curs.execute(q, h)

            dates = []
            logins = []
            logouts = []
            timeouts = []
            failures = []

            for r in curs.fetchall():
                dates.append(r[0])
                logins.append(r[1])
                logouts.append(r[2])
                timeouts.append(r[3])
                failures.append(r[4])

            if not logins:
                logins = [0,]
            if not logouts:
                logouts = [0,]
            if not timeouts:
                timeouts = [0,]
            if not failures:
                failures = [0,]

            rp = sql_helper.get_required_points(start_date, end_date,
                                            mx.DateTime.DateTimeDeltaFromSeconds(time_interval))

            ks = KeyStatistic(_('Average Logins'), sum(logins) / len(rp),
                              N_('Events')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max Logins'), max(logins),
                              N_('Events')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Average Logouts'), sum(logouts) / len(rp),
                              N_('Events')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max Logouts'), max(logouts),
                              N_('Events')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Average Timeouts'), sum(timeouts) / len(rp),
                              N_('Events')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max Timeouts'), max(timeouts),
                              N_('Events')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Average Failures'), sum(failures) / len(rp),
                              N_('Events')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max Failures'), max(failures),
                              N_('Events')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Average Events'), sum(logins+logouts+failures) / len(rp),
                              N_('Events')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max Events'), max(logouts+logouts+failures),
                              N_('Events')+'/'+_(unit))
            lks.append(ks)

        finally:
            conn.commit()

        plot = Chart(type=STACKED_BAR_CHART,
                     title=self.title,
                     xlabel=_(unit),
                     ylabel=_('Events'),
                     major_formatter=HOUR_FORMATTER,
                     required_points=rp)
        plot.add_dataset(dates, logins, label=_('Logins'),
                         color=colors.goodness)
        plot.add_dataset(dates, logouts, label=_('Logouts'),
                         color=colors.detected)
        plot.add_dataset(dates, logouts, label=_('Timeouts'),
                         color=colors.detected)
        plot.add_dataset(dates, failures, label=_('Failures'),
                         color=colors.badness)

        return (lks, plot)


class TopUsers(Graph):
    def __init__(self):
        Graph.__init__(self, 'top-users', _('Top Captive Portal Users'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email or host or user:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """
SELECT login_name,count(*)::int as logins
FROM reports.n_capture_user_events
WHERE NOT login_name IS NULL
AND event_info != 'FAILED'
AND time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone
GROUP BY login_name"""

        conn = sql_helper.get_connection()
        try:
            curs = conn.cursor()

            curs.execute(query, (one_week, ed))

            lks = []
            pie_data = {}

            for r in curs.fetchall():
                uid = r[0]
                logins = r[1]

                ks = KeyStatistic(uid, logins, N_('Logins'))
                lks.append(ks)

                pie_data[uid] = logins
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART, title=self.title)

        plot.add_pie_dataset(pie_data, display_limit=10)

        return (lks, plot, 10)

class TopBlockedClients(Graph):
    def __init__(self):
        Graph.__init__(self, 'top-blocked-users', _('Top Blocked Clients'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email or host or user:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """
SELECT client_address,sum(blocks)::int as blocks
FROM reports.n_capture_block_totals
WHERE NOT client_address IS NULL AND trunc_time >= %s::timestamp without time zone AND trunc_time < %s::timestamp without time zone
GROUP BY client_address"""

        conn = sql_helper.get_connection()
        try:
            curs = conn.cursor()

            curs.execute(query, (one_week, ed))

            lks = []
            pie_data = {}

            for r in curs.fetchall():
                client_address = r[0]
                blocks = r[1]

                ks = KeyStatistic(client_address, blocks, _('Blocks'))
                lks.append(ks)

                pie_data[client_address] = blocks
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART, title=self.title)

        plot.add_pie_dataset(pie_data, display_limit=10)

        return (lks, plot, 10)


class userDetail(DetailSection):
    def __init__(self):
        DetailSection.__init__(self, 'capture-user-events', _('Capture User Events'))

    def get_columns(self, host=None, user=None, email=None):
        if email or user or host:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date'),
              ColumnDesc('login_name', _('Login Name')),
              ColumnDesc('event_info', _('Type'))]

        return rv

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if email or host or user:
            return None

        sql = """
SELECT time_stamp, login_name,
CASE WHEN event_info = 'LOGIN' THEN '%s'
     WHEN event_info = 'FAILED' THEN '%s' END
FROM reports.n_capture_user_events
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone
ORDER BY time_stamp DESC
""" % (LOGIN, FAILED,
       DateFromMx(start_date),
       DateFromMx(end_date))

        return sql

class BlockDetail(DetailSection):
    def __init__(self):
        DetailSection.__init__(self, 'capture-rule-events', _('Capture Rule Events'))

    def get_columns(self, host=None, user=None, email=None):
        if email or user or host:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date'),
              ColumnDesc('capture_rule_index', _('Rule Applied')),
              ColumnDesc('capture_blocked', _('Blocked'))]

        return rv

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if email or host or user:
            return None

        sql = """
SELECT time_stamp, host(client_address), client_port,
       host(server_address), server_port, capture_rule_index, capture_blocked::text
FROM reports.sessions
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone
ORDER BY time_stamp DESC
""" % (DateFromMx(start_date),
       DateFromMx(end_date))

        return sql

reports.engine.register_node(Capture())
