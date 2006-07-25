// Copyright (c) 2006 Metavize Inc.
// All rights reserved.

function ApplicationPanel(parent)
{

    if (0 == arguments.length) {
        return;
    }

    DwtComposite.call(this, parent, "ApplicationPanel", DwtControl.RELATIVE_STYLE);

    this._init();
};

ApplicationPanel.prototype = new DwtComposite();
ApplicationPanel.prototype.constructor = ApplicationPanel;

// constants ------------------------------------------------------------------

ApplicationPanel.DEFAULT_TITLE = "Applications";

// public methods -------------------------------------------------------------

ApplicationPanel.prototype.redraw = function()
{
    this._applicationList.setUI();
};

ApplicationPanel.prototype.setTitle = function(title)
{
    this._title.setText(title || ApplicationPanel.DEFAULT_TITLE);
};

ApplicationPanel.prototype.addApplication = function(app)
{
    return this._applicationList.addApplication(app);
};

ApplicationPanel.prototype.clearApplications = function()
{
    return this._applicationList.clearApplications();
};

ApplicationPanel.prototype.addSelectionListener = function(l)
{
    this._applicationList.addSelectionListener(l);
};

ApplicationPanel.prototype.addActionListener = function(l)
{
    this._applicationList.addActionListener(l);
};

// private methods ------------------------------------------------------------

ApplicationPanel.prototype._init = function()
{
    var titleId = Dwt.getNextId();
    var listId = Dwt.getNextId();

    var html = [];
    html.push("<table width='100%' height='100%'>");

    html.push("<tr>");
    html.push("<td>");
    html.push("<div id='");
    html.push(titleId);
    html.push("'/>");
    html.push("</td>");
    html.push("</tr>");

    html.push("<tr>");

    html.push("<td style='width: 100%; height: 100%'>");
    html.push("<div style='width: 100%; height: 100%' id='");
    html.push(listId);
    html.push("'/>");
    html.push("</td>");

    html.push("</tr>");

    html.push("</table>");

    this.setContent(html.join(""));

    this._title = new DwtLabel(this, DwtLabel.ALIGN_LEFT, "ListTitle",
                               DwtControl.RELATIVE_STYLE);
    this._title.setText(ApplicationPanel.DEFAULT_TITLE);
    this._title.reparentHtmlElement(titleId);

    this._applicationList = new ApplicationList(this);
    this._applicationList.reparentHtmlElement(listId);
}
