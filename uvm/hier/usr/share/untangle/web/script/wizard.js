/*global
 Ext, Ung, Webui, rpc:true, i18n:true
 */
Ext.define('Ung.Wizard', {
    extend: 'Ext.container.Viewport',
    controller: 'wizard',
    layout: 'auto',
    name: 'wizard',
    maxWidth: 'auto',
    minWidth: 'auto',
    border: 0,
    currentPage: null,
    hasCancel: false,
    modalFinish: false, //can not go back or cancel on finish step
    finished: false,
    showLogo: false,
    items: [{
        xtype: 'panel',
        layout: 'card',
        itemId: 'content',
        height: 500,
        items: []
    }, {
        xtype: 'toolbar',
        dock: 'bottom',
        items: [{
            xtype: 'button',
            itemId: 'prevBtn',
            hidden: true,
            scale: 'medium',
            listeners: {
                click: 'onPrev'
            }
        }, '->', {
            xtype: 'button',
            itemId: 'cancelBtn',
            scale: 'medium',
            text: i18n._('Cancel'),
            hidden: true,
            listeners: {
                click: 'onCancel'
            }
        }, {
            xtype: 'button',
            itemId: 'nextBtn',
            scale: 'medium',
            listeners: {
                click: 'onNext'
            }
        }]
    }]
});

Ext.define('Ung.WizardController', {
    extend : 'Ext.app.ViewController',
    alias: 'controller.wizard',
    init: function () {
        Ext.getWin().addKeyListener(13, function () {
            this.onNext();
        }, this);

        this.view.setStyle({
            maxWidth: this.view.maxWidth + 'px',
            minWidth: this.view.minWidth + 'px',
            margin: '0 auto'
        });

        this.prevBtn = this.view.down('#prevBtn');
        this.nextBtn = this.view.down('#nextBtn');
        this.cancelBtn = this.view.down('#cancelBtn');
        this.content = this.view.down('#content');
        var items = [], i;
        //console.log(this.view.cards);
        for (i = 0; i < this.view.cards.length; i += 1) {
            items.push(this.view.cards[i].panel);
        }
        this.content.add(items);

        this.loadPage(0);
    },

    onPrev: function () {
        this.goToPage(this.view.currentPage - 1);
    },

    onNext: function () {
        this.goToPage(this.view.currentPage + 1);
    },

    onCancel: function () {
        Ext.emptyFn();
    },

    goToPage: function (index) {
        //this.content.setActiveItem(this.currentIndex);

        var handler = null, pageNo = this.view.currentPage;
        if (pageNo <= index) {
            if (Ext.isFunction(this.view.cards[pageNo].onValidate)) {
                if (!this.view.cards[pageNo].onValidate()) {
                    return;
                }
            }
            handler = this.view.cards[pageNo].onNext;
        } else if (pageNo > index) {
            handler = this.view.cards[pageNo].onPrevious;
        }

        // Call the handler if it is defined
        if (Ext.isFunction(handler)) {
            handler(Ext.bind(this.loadPage, this, [index]));
        } else {
            this.loadPage(index);
        }
    },

    loadPage: function (index) {
        if (index < 0 || index >= this.view.cards.length) {
            return;
        }
        this.view.currentPage = index;
        var card = this.view.cards[this.view.currentPage];
        if (Ext.isFunction(card.onLoad)) {
            card.onLoad(Ext.bind(this.syncWizard, this));
        } else {
            this.syncWizard();
        }
    },

    syncWizard : function () {
        var pageNo = this.view.currentPage;
        this.content.setActiveItem(pageNo);

        if (pageNo === 0) {
            this.prevBtn.hide();
        } else {
            this.prevBtn.show();
            this.prevBtn.setText('&laquo; ' + this.view.cards[pageNo - 1].title);
        }

        if (pageNo == (this.view.cards.length - 1)) {
            if (this.view.modalFinish) {
                this.nextBtn.setText(i18n._('Close'));
                if (this.view.hasCancel) {
                    this.cancelBtn.hide();
                }
                this.view.finished = true;
            } else {
                this.nextBtn.setText(i18n._('Finish'));
            }
        } else {
            this.nextBtn.setText(this.view.cards[pageNo + 1].title + ' &raquo;');
            if (this.view.hasCancel) {
                this.cancelBtn.show();
            }
        }
    }
});