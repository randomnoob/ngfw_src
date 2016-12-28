{
    "uniqueId": "application-control-OAI5zmhxOM",
    "category": "Application Control",
    "description": "The amount of bandwidth per top application.",
    "displayOrder": 100,
    "enabled": true,
    "javaClass": "com.untangle.node.reports.ReportEntry",
    "orderDesc": false,
    "units": "bytes/s",
    "readOnly": true,
    "table": "session_minutes",
    "timeDataInterval": "AUTO",
    "timeDataDynamicValue": "(s2c_bytes+c2s_bytes)/60",
    "timeDataDynamicColumn": "application_control_application",
    "timeDataDynamicLimit": "10",
    "timeDataDynamicAggregationFunction": "sum",
    "timeDataDynamicAllowNull": true,
    "timeStyle": "AREA_STACKED",
    "title": "Top Applications Usage",
    "type": "TIME_GRAPH_DYNAMIC"
}
