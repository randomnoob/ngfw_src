{
    "category": "Spam Blocker",
    "conditions": [
        {
            "column": "addr_kind",
            "javaClass": "com.untangle.node.reports.SqlCondition",
            "operator": "=",
            "value": "B"
        },
        {
            "column": "spam_blocker_action",
            "javaClass": "com.untangle.node.reports.SqlCondition",
            "operator": "=",
            "value": "Q"
        }
    ],
    "defaultColumns": ["time_stamp","hostname","s_server_addr","addr","sender","subject","spam_blocker_is_spam","spam_blocker_action","spam_blocker_score","spam_blocker_tests_string"],
    "description": "All emails marked as Spam and quarantined.",
    "displayOrder": 30,
    "javaClass": "com.untangle.node.reports.EventEntry",
    "table": "mail_addrs",
    "title": "Quarantined Events",
    "uniqueId": "spam-blocker-1Q3N4Z240O"
}
