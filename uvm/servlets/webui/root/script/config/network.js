if (!Ung.hasResource["Ung.Network"]) {
    Ung.hasResource["Ung.Network"] = true;

    Ung.NetworkUtil={
        getPortForwardMatchers: function (settingsCmp) {
            return [
                {name:"DST_LOCAL",displayName: settingsCmp.i18n._("Destined Local"), type: "boolean", visible: true},
                {name:"DST_ADDR",displayName: settingsCmp.i18n._("Destination Address"), type: "text", visible: true, vtype:"ipMatcher"},
                {name:"DST_PORT",displayName: settingsCmp.i18n._("Destination Port"), type: "text",vtype:"portMatcher", visible: true},
                {name:"DST_INTF",displayName: settingsCmp.i18n._("Destination Interface"), type: "checkgroup", values: Ung.Util.getInterfaceList(true, true), visible: true, allowInvert: false},
                {name:"SRC_ADDR",displayName: settingsCmp.i18n._("Source Address"), type: "text", visible: true, vtype:"ipMatcher"},
                {name:"SRC_PORT",displayName: settingsCmp.i18n._("Source Port"), type: "text",vtype:"portMatcher", visible: false},
                {name:"SRC_INTF",displayName: settingsCmp.i18n._("Source Interface"), type: "checkgroup", values: Ung.Util.getInterfaceList(false, false), visible: true, allowInvert: false},
                {name:"PROTOCOL",displayName: settingsCmp.i18n._("Protocol"), type: "checkgroup", values: [["TCP","TCP"],["UDP","UDP"],["ICMP","ICMP"],["GRE","GRE"],["ESP","ESP"],["AH","AH"],["SCTP","SCTP"]], visible: true, allowInvert: false}
            ];
        },
        getNatRuleMatchers: function (settingsCmp) {
            return [
                {name:"DST_ADDR",displayName: settingsCmp.i18n._("Destination Address"), type: "text", visible: true, vtype:"ipMatcher"},
                {name:"DST_PORT",displayName: settingsCmp.i18n._("Destination Port"), type: "text",vtype:"portMatcher", visible: true},
                {name:"DST_INTF",displayName: settingsCmp.i18n._("Destination Interface"), type: "checkgroup", values: Ung.Util.getInterfaceList(true, true), visible: true, allowInvert: false},
                {name:"SRC_ADDR",displayName: settingsCmp.i18n._("Source Address"), type: "text", visible: true, vtype:"ipMatcher"},
                {name:"SRC_PORT",displayName: settingsCmp.i18n._("Source Port"), type: "text",vtype:"portMatcher", visible: false},
                {name:"SRC_INTF",displayName: settingsCmp.i18n._("Source Interface"), type: "checkgroup", values: Ung.Util.getInterfaceList(false, false), visible: true, allowInvert: false},
                {name:"PROTOCOL",displayName: settingsCmp.i18n._("Protocol"), type: "checkgroup", values: [["TCP","TCP"],["UDP","UDP"]], visible: false, allowInvert: false}
            ];
        },
        getBypassRuleMatchers: function (settingsCmp) {
            return [
                {name:"DST_ADDR",displayName: settingsCmp.i18n._("Destination Address"), type: "text", visible: true, vtype:"ipMatcher"},
                {name:"DST_PORT",displayName: settingsCmp.i18n._("Destination Port"), type: "text",vtype:"portMatcher", visible: true},
                {name:"DST_INTF",displayName: settingsCmp.i18n._("Destination Interface"), type: "checkgroup", values: Ung.Util.getInterfaceList(true, true), visible: true, allowInvert: false},
                {name:"SRC_ADDR",displayName: settingsCmp.i18n._("Source Address"), type: "text", visible: true, vtype:"ipMatcher"},
                {name:"SRC_PORT",displayName: settingsCmp.i18n._("Source Port"), type: "text",vtype:"portMatcher", visible: false},
                {name:"SRC_INTF",displayName: settingsCmp.i18n._("Source Interface"), type: "checkgroup", values: Ung.Util.getInterfaceList(false, false), visible: true, allowInvert: false},
                {name:"PROTOCOL",displayName: settingsCmp.i18n._("Protocol"), type: "checkgroup", values: [["TCP","TCP"],["UDP","UDP"]], visible: true, allowInvert: false}
            ];
        }
    };


    Ext.define('Ung.InterfaceEditorWindow', {
        extend:'Ung.EditWindow',
        validate: null,
        record: null,
        initialRecordData: null,
        sizeToGrid: true,
        sizeToComponent: null,
        title: i18n._('Edit Interface'),
        reRenderFields: Ext.bind( function() {
            var configTypeValue = Ext.getCmp('interface_configType').getValue();
            var isWan = Ext.getCmp('interface_isWan').getValue();

            // hide everything
            Ext.getCmp('interface_isWan').setVisible(false);
            Ext.getCmp('interface_bridgedTo').setVisible(false);
            Ext.getCmp('interface_v4Config').setVisible(false);
            Ext.getCmp('interface_v4ConfigType').setVisible(false);
            Ext.getCmp('interface_v4StaticAddress').setVisible(false);
            Ext.getCmp('interface_v4StaticNetmask').setVisible(false);
            Ext.getCmp('interface_v4StaticGateway').setVisible(false);
            Ext.getCmp('interface_v4StaticDns1').setVisible(false);
            Ext.getCmp('interface_v4StaticDns2').setVisible(false);
            Ext.getCmp('interface_v4AutoAddressOverride').setVisible(false);
            Ext.getCmp('interface_v4AutoNetmaskOverride').setVisible(false);
            Ext.getCmp('interface_v4AutoGatewayOverride').setVisible(false);
            Ext.getCmp('interface_v4AutoDns1Override').setVisible(false);
            Ext.getCmp('interface_v4AutoDns2Override').setVisible(false);
            Ext.getCmp('interface_v4PPPoEUsername').setVisible(false);
            Ext.getCmp('interface_v4PPPoEPassword').setVisible(false);
            Ext.getCmp('interface_v4PPPoEUsePeerDns').setVisible(false);
            Ext.getCmp('interface_v4PPPoEDns1').setVisible(false);
            Ext.getCmp('interface_v4PPPoEDns2').setVisible(false);
            Ext.getCmp('interface_v4NatEgressTraffic').setVisible(false); 
            Ext.getCmp('interface_v4NatIngressTraffic').setVisible(false); 
            Ext.getCmp('interface_v6Config').setVisible(false);
            Ext.getCmp('interface_v6ConfigType').setVisible(false);
            Ext.getCmp('interface_v6StaticAddress').setVisible(false);
            Ext.getCmp('interface_v6StaticPrefixLength').setVisible(false);
            Ext.getCmp('interface_v6StaticGateway').setVisible(false);
            Ext.getCmp('interface_v6StaticDns1').setVisible(false);
            Ext.getCmp('interface_v6StaticDns2').setVisible(false);
            Ext.getCmp('interface_dhcp').setVisible(false);

            // if config disabled show nothing
            if ( configTypeValue == "DISABLED") {
                return;
            }

            // if config bridged just show the one field 
            if ( configTypeValue == "BRIDGED") {
                Ext.getCmp('interface_bridgedTo').setVisible(true);
                return;
            }

            // if config addressed show necessary options
            if ( configTypeValue == "ADDRESSED") {
                Ext.getCmp('interface_isWan').setVisible(true);
                Ext.getCmp('interface_v4Config').setVisible(true);
                Ext.getCmp('interface_v6Config').setVisible(true);

                // if not a WAN, must configure statically
                // if a WAN, can use auto or static
                if ( isWan ) {
                    Ext.getCmp('interface_v4ConfigType').setVisible(true); //show full config options for WANs
                    Ext.getCmp('interface_v6ConfigType').setVisible(true); //show full config options for WANs
                    Ext.getCmp('interface_v4NatEgressTraffic').setVisible(true); // show NAT egress option on WANs
                } else {
                    Ext.getCmp('interface_v4ConfigType').setValue("STATIC"); //don't allow auto/pppoe for non-WAN
                    Ext.getCmp('interface_v6ConfigType').setValue("STATIC"); //don't allow auto/pppoe for non-WAN
                    Ext.getCmp('interface_v4StaticGateway').setVisible(false); // no gateways for non-WAN
                    Ext.getCmp('interface_v6StaticGateway').setVisible(false); // no gateways for non-WAN
                    Ext.getCmp('interface_v4NatIngressTraffic').setVisible(true); // show NAT ingress options on non-WANs
                    Ext.getCmp('interface_dhcp').setVisible(true); // show DHCP options on non-WANs
                }
                
                // if static show static fields
                // if auto show override fields (auto is only allowed on WANs)
                // if pppoe show pppoe fields (pppoe is only allowed on WANs)
                if ( Ext.getCmp('interface_v4ConfigType').getValue() == "STATIC" ) {
                    Ext.getCmp('interface_v4StaticAddress').setVisible(true);
                    Ext.getCmp('interface_v4StaticNetmask').setVisible(true);
                    if (isWan) {
                        Ext.getCmp('interface_v4StaticGateway').setVisible(true);
                        Ext.getCmp('interface_v4StaticDns1').setVisible(true);
                        Ext.getCmp('interface_v4StaticDns2').setVisible(true);
                    }
                } else if ( Ext.getCmp('interface_v4ConfigType').getValue() == "AUTO" ) {
                    Ext.getCmp('interface_v4AutoAddressOverride').setVisible(true);
                    Ext.getCmp('interface_v4AutoNetmaskOverride').setVisible(true);
                    Ext.getCmp('interface_v4AutoGatewayOverride').setVisible(true);
                    Ext.getCmp('interface_v4AutoDns1Override').setVisible(true);
                    Ext.getCmp('interface_v4AutoDns2Override').setVisible(true);
                } else if ( Ext.getCmp('interface_v4ConfigType').getValue() == "PPPOE" ) {
                    Ext.getCmp('interface_v4PPPoEUsername').setVisible(true);
                    Ext.getCmp('interface_v4PPPoEPassword').setVisible(true);
                    Ext.getCmp('interface_v4PPPoEUsePeerDns').setVisible(true);
                    if ( Ext.getCmp('interface_v4PPPoEUsePeerDns').getValue() == false ) {
                        Ext.getCmp('interface_v4PPPoEDns1').setVisible(true);
                        Ext.getCmp('interface_v4PPPoEDns2').setVisible(true);
                    }
                }

                // if static show static fields
                // if auto show override fields
                if ( Ext.getCmp('interface_v6ConfigType').getValue() == "STATIC" ) {
                    Ext.getCmp('interface_v6StaticAddress').setVisible(true);
                    Ext.getCmp('interface_v6StaticPrefixLength').setVisible(true);
                    if (isWan) {
                        Ext.getCmp('interface_v6StaticGateway').setVisible(true);
                        Ext.getCmp('interface_v6StaticDns1').setVisible(true);
                        Ext.getCmp('interface_v6StaticDns2').setVisible(true);
                    }
                } else /* auto */ {
                    // no overriding in IPv6 so nothing to show
                }
            }

        }, this ),
        initComponent: function() {
            this.inputLines = [{
                xtype:'textfield',
                id: "interface_name",
                dataIndex: "name",
                fieldLabel: i18n._("Interface Name"),
                width: 300
            }, {
                xtype: "combo",
                id: "interface_configType",
                allowBlank: false,
                dataIndex: "configType",
                fieldLabel: i18n._("Config Type"),
                editable: false,
                store: [["ADDRESSED",i18n._('Addressed')], ["BRIDGED",i18n._('Bridged')], ["DISABLED",i18n._('Disabled')]],
                valueField: "value",
                displayField: "displayName",
                queryMode: 'local',
                triggerAction: 'all',
                listClass: 'x-combo-list-small',
                listeners: {
                    select: this.reRenderFields
                }
            }, {
                xtype:'checkbox',
                id: "interface_isWan",
                dataIndex: "isWan",
                fieldLabel: i18n._("is WAN Interface"),
                listeners: {
                    change: Ext.bind( function( f, val ) {
                        if ( val ) {
                            Ext.getCmp('interface_v4NatEgressTraffic').setValue( true ); // default to NAT egress on WANs
                            Ext.getCmp('interface_v4NatIngressTraffic').setValue( false ); // disable hidden option
                        } else {
                            Ext.getCmp('interface_v4NatIngressTraffic').setValue( true ); // default to NAT ingress on non-WANs
                            Ext.getCmp('interface_v4NatEgressTraffic').setValue( false ); // disable hidden option
                        }

                        this.reRenderFields();
                    }, this)}
            }, {
                id:'interface_v4Config',
                style: "border:1px solid;", // UGLY FIXME
                xtype:'fieldset',
                title:i18n._("IPv4 Configuration"),
                collapsible: true,
                collapsed: false,
                items: [{
                    xtype: "combo",
                    id: "interface_v4ConfigType",
                    allowBlank: false,
                    dataIndex: "v4ConfigType",
                    fieldLabel: i18n._("Config Type"),
                    editable: false,
                    store: [ ["AUTO",i18n._('Auto (DHCP)')], ["STATIC",i18n._('Static')],  ["PPPOE",i18n._('PPPoE')]],
                    valueField: "value",
                    displayField: "displayName",
                    queryMode: 'local',
                    triggerAction: 'all',
                    listClass: 'x-combo-list-small',
                    listeners: {
                        select: this.reRenderFields
                    }
                }, {
                    xtype:'textfield',
                    id: "interface_v4StaticAddress",
                    dataIndex: "v4StaticAddress",
                    fieldLabel: i18n._("Address"),
                    allowBlank: false,
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4StaticNetmask",
                    dataIndex: "v4StaticNetmask",
                    fieldLabel: i18n._("Netmask"),
                    allowBlank: false,
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4StaticGateway",
                    dataIndex: "v4StaticGateway",
                    fieldLabel: i18n._("Gateway"),
                    allowBlank: false,
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4StaticDns1",
                    dataIndex: "v4StaticDns1",
                    fieldLabel: i18n._("Primary DNS"),
                    allowBlank: false,
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4StaticDns2",
                    dataIndex: "v4StaticDns2",
                    fieldLabel: i18n._("Secondary DNS"),
                    allowBlank: false,
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4AutoAddressOverride",
                    dataIndex: "v4AutoAddressOverride",
                    fieldLabel: i18n._("Address Override"),
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4AutoNetmaskOverride",
                    dataIndex: "v4AutoNetmaskOverride",
                    fieldLabel: i18n._("Netmask Override"),
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4AutoGatewayOverride",
                    dataIndex: "v4AutoGatewayOverride",
                    fieldLabel: i18n._("Gateway Override"),
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4AutoNetmaskOverride",
                    dataIndex: "v4AutoNetmaskOverride",
                    fieldLabel: i18n._("Netmask Override"),
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4AutoDns1Override",
                    dataIndex: "v4AutoDns1Override",
                    fieldLabel: i18n._("Primary DNS Override"),
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4AutoDns2Override",
                    dataIndex: "v4AutoDns2Override",
                    fieldLabel: i18n._("Secondary DNS Override"),
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4PPPoEUsername",
                    dataIndex: "v4PPPoEUsername",
                    fieldLabel: i18n._("Username"),
                    width: 300
                }, {
                    xtype:'textfield',
                    inputType:'password',
                    id: "interface_v4PPPoEPassword",
                    dataIndex: "v4PPPoEPassword",
                    fieldLabel: i18n._("Password"),
                    width: 300
                }, {
                    xtype:'checkbox',
                    id: "interface_v4PPPoEUsePeerDns",
                    dataIndex: "v4PPPoEUsePeerDns",
                    fieldLabel: i18n._("Use Peer DNS"),
                    width: 300,
                    listeners: {
                        change: this.reRenderFields
                    }
                }, {
                    xtype:'textfield',
                    id: "interface_v4PPPoEDns1",
                    dataIndex: "v4PPPoEDns1",
                    fieldLabel: i18n._("Primary DNS"),
                    vtype: "ipAddress",
                    width: 300
                }, {
                    xtype:'textfield',
                    id: "interface_v4PPPoEDns2",
                    dataIndex: "v4PPPoEDns2",
                    fieldLabel: i18n._("Secondary DNS"),
                    vtype: "ipAddress",
                    width: 300
                },{
                    id:'interface_v4ExtraOptions',
                    style: "border:1px solid;", // UGLY FIXME
                    xtype:'fieldset',
                    title:i18n._("IPv4 Options"),
                    collapsible: true,
                    collapsed: false,
                    items: [{
                        xtype:'checkbox',
                        id: "interface_v4NatEgressTraffic",
                        dataIndex: "v4NatEgressTraffic",
                        boxLabel: i18n._("NAT traffic exiting this interface (and bridged peers)"),
                        width: 400
                    }, {
                        xtype:'checkbox',
                        id: "interface_v4NatIngressTraffic",
                        dataIndex: "v4NatIngressTraffic",
                        boxLabel: i18n._("NAT traffic coming from this interface (and bridged peers)"),
                        width: 400
                    }]
                }]
            }, {
                id:'interface_v6Config',
                style: "border:1px solid;", // UGLY FIXME
                xtype:'fieldset',
                border:true,
                title:i18n._("IPv6 Configuration"),
                collapsible: true,
                collapsed: true,
                items: [{
                    border:true,
                    xtype: "combo",
                    id: "interface_v6ConfigType",
                    allowBlank: false,
                    dataIndex: "v6ConfigType",
                    fieldLabel: i18n._("Config Type"),
                    editable: false,
                    store: [ ["AUTO",i18n._('Auto (SLAAC/RA)')], ["STATIC",i18n._('Static')] ],
                    valueField: "value",
                    displayField: "displayName",
                    queryMode: 'local',
                    triggerAction: 'all',
                    listClass: 'x-combo-list-small',
                    listeners: {
                        change: this.reRenderFields
                    }
                }, {
                    xtype:'textfield',
                    id: "interface_v6StaticAddress",
                    dataIndex: "v6StaticAddress",
                    fieldLabel: i18n._("Address"),
                    allowBlank: false,
                    //vtype: "ipAddress",
                    width: 500
                }, {
                    xtype:'textfield',
                    id: "interface_v6StaticPrefixLength",
                    dataIndex: "v6StaticPrefixLength",
                    fieldLabel: i18n._("Prefix Length"),
                    allowBlank: false,
                    width: 150
                }, {
                    xtype:'textfield',
                    id: "interface_v6StaticGateway",
                    dataIndex: "v6StaticGateway",
                    fieldLabel: i18n._("Gateway"),
                    allowBlank: false,
                    //vtype: "ipAddress",
                    width: 500
                }, {
                    xtype:'textfield',
                    id: "interface_v6StaticDns1",
                    dataIndex: "v6StaticDns1",
                    fieldLabel: i18n._("Primary DNS"),
                    //vtype: "ipAddress",
                    width: 500
                }, {
                    xtype:'textfield',
                    id: "interface_v6StaticDns2",
                    dataIndex: "v6StaticDns2",
                    fieldLabel: i18n._("Secondary DNS"),
                    //vtype: "ipAddress",
                    width: 500
                }]
            }, {
                id:'interface_dhcp',
                style: "border:1px solid;", // UGLY FIXME
                xtype:'fieldset',
                border:true,
                title:i18n._("DHCP Configuration"),
                collapsible: true,
                collapsed: false,
                items: [{
                    xtype:'checkbox',
                    id: "interface_dhcpEnabled",
                    dataIndex: "dhcpEnabled",
                    boxLabel: i18n._("Enable DHCP")
                }, {
                    xtype:'textfield',
                    id: "interface_dhcpRangeStart",
                    dataIndex: "dhcpRangeStart",
                    fieldLabel: i18n._("Range Start"),
                    vtype: "ipAddress"
                }, {
                    xtype:'textfield',
                    id: "interface_dhcpRangeEnd",
                    dataIndex: "dhcpRangeEnd",
                    fieldLabel: i18n._("Range End"),
                    vtype: "ipAddress"
                }, {
                    xtype:'textfield',
                    id: "interface_dhcpLeaseDuration",
                    dataIndex: "dhcpLeaseDuration",
                    fieldLabel: i18n._("Lease Duration")
                }, {
                    xtype:'textfield',
                    id: "interface_dhcpLeaseLimit",
                    dataIndex: "dhcpLeaseLimit",
                    fieldLabel: i18n._("Lease Limit")
                }, {
                    xtype:'checkbox',
                    id: "interface_dhcpAuthoritative",
                    dataIndex: "dhcpAuthoritative",
                    fieldLabel: i18n._("Authoritative")
                }, {
                    xtype:'textfield',
                    id: "interface_dhcpGatewayOverride",
                    dataIndex: "dhcpGatewayOverride",
                    fieldLabel: i18n._("Gateway Override"),
                    labelStyle: 'width:120px',
                    vtype: "ipAddress"
                }, {
                    xtype:'textfield',
                    id: "interface_dhcpNetmaskOverride",
                    dataIndex: "dhcpNetmaskOverride",
                    fieldLabel: i18n._("Netmask Override"),
                    labelStyle: 'width:120px',
                    vtype: "ipAddress"
                }, {
                    xtype:'textfield',
                    id: "interface_dhcpDnsOverride",
                    dataIndex: "dhcpDnsOverride",
                    fieldLabel: i18n._("DNS Override"),
                    labelStyle: 'width:120px',
                    vtype: "ipAddress"
                }]
            }, {
                xtype: "combo",
                id: "interface_bridgedTo",
                dataIndex: "bridgedTo",
                fieldLabel: i18n._( "Bridged To" ),
                store: Ung.Util.getInterfaceAddressedList(),
                valueField: "value",
                displayField: "displayName",
                width: 180,
                listWidth: 70,
                triggerAction: "all",
                queryMode: 'local',
                editable: false
            }, {
                html: "TODO: aliases grid<br/>"
            }, {
                html: "TODO: MTU settings<br/>"
            }];

            this.items = [Ext.create('Ext.panel.Panel',{
                name: "EditInterface",
                parentId: this.getId(),
                autoScroll: true,
                bodyStyle: 'padding:10px 10px 0px 10px;',
                items: this.inputLines
            })];

            this.callParent(arguments);
        },
        populate: function(record) {
            // refresh interface selector store (may have changed since last display)
            Ext.getCmp('interface_bridgedTo').getStore().loadData( Ung.Util.getInterfaceAddressedList() );
            
            return this.populateTree(record);
        },
        populateTree: function(record) {
            this.record = record;
            this.initialRecordData = Ext.encode(record.data);
            
            this.populateChild(this, record);

            this.reRenderFields();
        },
        populateChild: function(component,record) {
            if ( component == null ) {
                return;
            }

            if (component.dataIndex != null && component.setValue ) {
                component.suspendEvents();
                component.setValue(record.get(component.dataIndex));
                component.resumeEvents();
            }

            var items = null;
            if (component.items) {
                items = component.items.getRange();
            }

            if ( items ) {
                for (var i = 0; i < items.length; i++) {
                    var item = null;
                    if ( items.get != null ) {
                        item = items.get(i);
                    } else {
                        item = items[i];
                    }
                    this.populateChild( item, record );
                }
            }
        },
        // check if the form is valid;
        isFormValid: function() {
            /* FIXME */

            return true;
        },
        // updateAction is called to update the record after the edit
        updateAction: function() {
            if (!this.isFormValid()) {
                return;
            }

            this.updateActionTree( this.record );

            this.hide();
        },
        updateActionTree: function( record ) {
            this.updateActionChild(this, record);
        },
        updateActionChild: function( component, record ) {
            if ( component == null ) {
                return;
            }

            if (component.dataIndex != null && component.getValue ) {
                this.record.set(component.dataIndex, component.getValue());
            }

            var items = null;
            if (component.items) {
                items = component.items.getRange();
            }

            if ( items ) {
                for (var i = 0; i < items.length; i++) {
                    var item = null;
                    if ( items.get != null ) {
                        item = items.get(i);
                    } else {
                        item = items[i];
                    }
                    this.updateActionChild( item, record );
                }
            }
        },
        isDirty: function() {
            /* FIXME */
            
            return false;
        },
        closeWindow: function() {
            this.record.data = Ext.decode(this.initialRecordData);
            this.hide();
        }
    });
    
    Ext.define("Ung.Network", {
        extend: "Ung.ConfigWin",
        gridPortForwardRules: null,
        gridNatRules: null,
        gridBypassRules: null,
        gridStaticRoutes: null,
        panelInterfaces: null,
        panelHostName: null,
        panelPortForwardRules: null,
        panelNatRules: null,
        panelRoutes: null,
        initComponent: function() {
            this.breadcrumbs = [{
                title: i18n._("Configuration"),
                action: Ext.bind(function() {
                    this.cancelAction();
                }, this)
            }, {
                title: i18n._('Network')
            }];

            this.refreshSettings();
            
            // builds the tabs
            this.buildInterfaces();
            this.buildHostName();
            this.buildPortForwardRules();
            this.buildNatRules();
            this.buildBypassRules();
            this.buildRoutes();
            this.buildAdvanced();
            
            // builds the tab panel with the tabs
            var pageTabs = [ this.panelInterfaces, this.panelHostName, this.panelPortForwardRules, this.panelNatRules, this.panelBypassRules, this.panelRoutes, this.panelAdvanced ];
            this.buildTabPanel(pageTabs);
            this.callParent(arguments);
        },
        // Interfaces Panel
        buildInterfaces: function() {
            this.gridInterfaces = Ext.create('Ung.EditorGrid',{
                name: 'Interfaces',
                height: 400,
                settingsCmp: this,
                paginated: false,
                hasReorder: false,
                hasDelete: false,
                hasAdd: false,
                title: this.i18n._("Interfaces"),
                recordJavaClass: "com.untangle.uvm.network.InterfaceSettings",
                columnsDefaultSortable: false,
                dataProperty:'interfaces',
                fields: [{
                    name: 'interfaceId'
                }, {
                    name: 'name'
                }, {
                    name: 'physicalDev'
                }, {
                    name: 'systemDev'
                }, {
                    name: 'symbolicDev'
                }, {
                    name: 'configType'
                },{
                    name: 'isWan'
                }, {
                    name: 'bridgedTo'
                }, {
                    name: 'v4ConfigType'
                }, {
                    name: 'v4StaticAddress'
                }, {
                    name: 'v4StaticNetmask'
                }, {
                    name: 'v4StaticGateway'
                }, {
                    name: 'v4StaticDns1'
                }, {
                    name: 'v4StaticDns2'
                }, {
                    name: 'v4AutoAddressOverride'
                }, {
                    name: 'v4AutoNetmaskOverride'
                }, {
                    name: 'v4AutoGatewayOverride'
                }, {
                    name: 'v4AutoDns1Override'
                }, {
                    name: 'v4AutoDns2Override'
                }, {
                    name: 'v4PPPoEUsername'
                }, {
                    name: 'v4PPPoEPassword'
                }, {
                    name: 'v4PPPoEUsePeerDns'
                }, {
                    name: 'v4PPPoEDns1'
                }, {
                    name: 'v4PPPoEDns2'
                }, {
                    name: 'v4NatEgressTraffic'
                }, {
                    name: 'v4NatIngressTraffic'
                }, {
                    name: 'v6ConfigType'
                }, {
                    name: 'v6StaticAddress'
                }, {
                    name: 'v6StaticPrefixLength'
                }, {
                    name: 'v6StaticGateway'
                }, {
                    name: 'v6StaticDns1'
                }, {
                    name: 'v6StaticDns2'
                }, {
                    name: 'dhcpEnabled'
                }, {
                    name: 'dhcpAuthoritative'
                }, {
                    name: 'dhcpRangeStart'
                }, {
                    name: 'dhcpRangeEnd'
                }, {
                    name: 'dhcpLeaseDuration'
                }, {
                    name: 'dhcpLeaseLimit'
                }, {
                    name: 'dhcpGatewayOverride'
                }, {
                    name: 'dhcpNetmaskOverride'
                }, {
                    name: 'dhcpDnsOverride'
                }, {
                    name: 'javaClass'
                }],
                columns: [{
                    header: this.i18n._("Interface Id"),
                    width: 75,
                    dataIndex: 'interfaceId',
                    renderer: function(value) {
                        if (value < 0) {
                            return i18n._("new");
                        } else {
                            return value;
                        }
                    }
                }, {
                    header: this.i18n._("Name"),
                    dataIndex: 'name',
                    width:100
                }, {
                    header: this.i18n._("Physical Dev"),
                    dataIndex: 'physicalDev',
                    width:75
                }, {
                    header: this.i18n._("System Dev"),
                    dataIndex: 'systemDev',
                    width:75
                }, {
                    header: this.i18n._("Symbolic Dev"),
                    dataIndex: 'symbolicDev',
                    width:75
                }, {
                    header: this.i18n._("Config"),
                    dataIndex: 'configType',
                    width:100
                }, {
                    header: this.i18n._("is WAN"),
                    dataIndex: 'isWan',
                    width:55
                }, {
                    header: this.i18n._("v4 Config"),
                    dataIndex: 'v4ConfigType',
                    width:75
                }, {
                    header: this.i18n._("v6 Config"),
                    dataIndex: 'v6ConfigType',
                    width:75
                }],
                initComponent: function() {
                    this.rowEditor = Ext.create('Ung.InterfaceEditorWindow',{});
                    Ung.EditorGrid.prototype.initComponent.call(this);
                },
                bbar: [{
                    xtype: "button",
                    name: "remap_interfaces",
                    iconCls: 'icon-refresh',
                    text: this.i18n._("Remap Interfaces"),
                    handler: Ext.bind(function() {
                        //FIXME launch interface remapper 
                    }, this)
                }]
            });
            
            this.panelInterfaces = Ext.create('Ext.panel.Panel',{
                name: 'panelInterfaces',
                helpSource: 'network_interfaces',
                parentId: this.getId(),
                title: this.i18n._('Interfaces'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    title: this.i18n._('Note'),
                    html: this.i18n._(" <b>Interfaces</b> are legit. Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.")
                }, this.gridInterfaces]
            });
        },
        // HostName Panel
        buildHostName: function() {
            this.panelHostName = Ext.create('Ext.panel.Panel',{
                name: 'panelHostName',
                helpSource: 'network_interfaces',
                parentId: this.getId(),
                title: this.i18n._('Hostname'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    title: this.i18n._('Hostname'),
                    items: [{
                        xtype: 'container',
                        layout: 'column',
                        margin: '0 0 5 0',
                        items: [{
                            xtype: "textfield",
                            fieldLabel: this.i18n._("Hostname"),
                            emptyText: "hostname.example.com",
                            name: 'HostName',
                            value: this.settings.hostName,
                            listeners: {
                                "change": {
                                    fn: Ext.bind(function(elem, newValue) {
                                        this.settings.hostName = newValue;
                                    }, this)
                                }
                            }
                        },{
                            xtype: 'label',
                            html: this.i18n._("(eg: hostname.example.com)"),
                            cls: 'boxlabel'
                        }]
                    },{
                          xtype: 'container',
                          layout: 'column',
                          margin: '0 0 5 0',
                          items: [{
                              xtype: "textfield",
                              fieldLabel: this.i18n._("Domain Name"),
                              emptyText: "example.com",
                              name: 'DomainName',
                              value: this.settings.domainName,
                              listeners: {
                                  "change": {
                                      fn: Ext.bind(function(elem, newValue) {
                                          this.settings.domainName = newValue;
                                      }, this)
                                  }
                              }
                          },{
                              xtype: 'label',
                              html: this.i18n._("(eg: example.com)"),
                              cls: 'boxlabel'
                          }]
                        }]
                }, {
                    xtype: 'fieldset',
                    cls: 'description',
                    title: this.i18n._('Dynmaic DNS Service Configuration'),
                    items: [{
                        xtype: "checkbox",
                        fieldLabel: this.i18n._("Enabled"),
                        checked: this.settings.dynamicDnsServiceEnabled,
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.settings.dynamicDnsServiceEnabled = newValue;
                                }, this)
                            }
                        }
                    }, {
                        xtype: "combo",
                        fieldLabel: this.i18n._("Service"),
                        value: this.settings.dynamicDnsServiceName,
                        store: [['easydns','EasyDNS'], ['zoneedit','ZoneEdit'], ['dyndns','DynDNS'],['namecheap','Namecheap'],['dslreports','DSL-Reports'],['dnspark','DNSPark']],
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.settings.dynamicDnsServiceName = newValue;
                                }, this)
                            }
                        }
                    }, {
                        xtype: "textfield",
                        fieldLabel: this.i18n._("Username"),
                        value: this.settings.dynamicDnsServiceUsername,
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.settings.dynamicDnsServiceUsername = newValue;
                                }, this)
                            }
                        }
                    }, {
                        xtype: "textfield",
                        fieldLabel: this.i18n._("Password"),
                        value: this.settings.dynamicDnsServicePassword,
                        inputType: 'password',
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.settings.dynamicDnsServicePassword = newValue;
                                }, this)
                            }
                        }
                    }, {
                        xtype: "textfield",
                        fieldLabel: this.i18n._("Hostname(s)"),
                        value: this.settings.dynamicDnsServiceHostnames,
                        listeners: {
                            "change": {
                                fn: Ext.bind(function(elem, newValue) {
                                    this.settings.dynamicDnsServiceHostnames = newValue;
                                }, this)
                            }
                        }
                    }]
                }]
            });
        },
        // PortForwardRules Panel
        buildPortForwardRules: function() {
            this.gridPortForwardRules = Ext.create( 'Ung.EditorGrid', {
                anchor: '100% -80',
                name: 'Port Forward Rules',
                settingsCmp: this,
                paginated: false,
                hasReorder: true,
                addAtTop: false,
                emptyRow: {
                    "ruleId": -1,
                    "enabled": true,
                    "description": this.i18n._("[no description]"),
                    "javaClass": "com.untangle.uvm.network.PortForwardRule"
                },
                title: this.i18n._("Port Forward Rules"),
                recordJavaClass: "com.untangle.uvm.network.PortForwardRule",
                dataProperty:'portForwardRules',
                fields: [{
                    name: 'ruleId'
                }, {
                    name: 'enabled'
                }, {
                    name: 'newDestination'
                }, {
                    name: 'newPort'
                }, {
                    name: 'matchers'
                },{
                    name: 'description'
                }, {
                    name: 'javaClass'
                }],
                columns: [{
                    header: this.i18n._("Rule Id"),
                    width: 50,
                    dataIndex: 'ruleId',
                    renderer: function(value) {
                        if (value < 0) {
                            return i18n._("new");
                        } else {
                            return value;
                        }
                    }
                }, {
                    xtype:'checkcolumn',
                    header: this.i18n._("Enable"),
                    dataIndex: 'enabled',
                    fixed: true,
                    width:55
                }, {
                    header: this.i18n._("Description"),
                    width: 200,
                    dataIndex: 'description',
                    flex:1
                }, {
                    header: this.i18n._("New Destination"),
                    dataIndex: 'newDestination',
                    fixed: true,
                    width:150
                }, {
                    header: this.i18n._("New Port"),
                    dataIndex: 'newPort',
                    fixed: true,
                    width:55
                }],
                columnsDefaultSortable: false,

                initComponent: function() {
                    this.rowEditor = Ext.create('Ung.RowEditorWindow',{
                        grid: this,
                        sizeToComponent: this.settingsCmp,
                        inputLines: this.rowEditorInputLines,
                        rowEditorLabelWidth: 100,
                        populate: function(record, addMode) {
                            return this.populateTree(record, addMode);
                        },
                        // updateAction is called to update the record after the edit
                        updateAction: function() {
                            return this.updateActionTree();
                        },
                        isDirty: function() {
                            if (this.record !== null) {
                                if (this.inputLines) {
                                    for (var i = 0; i < this.inputLines.length; i++) {
                                        var inputLine = this.inputLines[i];
                                        if(inputLine.dataIndex!=null) {
                                            if (this.record.get(inputLine.dataIndex) != inputLine.getValue()) {
                                                return true;
                                            }
                                        }
                                        /* for fieldsets */
                                        if(inputLine.items !=null && inputLine.items.dataIndex != null) {
                                            if (this.record.get(inputLine.items.dataIndex) != inputLine.items.getValue()) {
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                            return Ext.getCmp('portForwardBuilder').isDirty();
                        },
                        isFormValid: function() {
                            for (var i = 0; i < this.inputLines.length; i++) {
                                var item = null;
                                if ( this.inputLines.get != null ) {
                                    item = this.inputLines.get(i);
                                } else {
                                    item = this.inputLines[i];
                                }
                                if ( item == null ) {
                                    continue;
                                }

                                if ( item.isValid != null) {
                                    if(!item.isValid()) {
                                        return false;
                                    }
                                } else if(item.items !=null && item.items.getCount()>0) {
                                    /* for fieldsets */
                                    for (var j = 0; j < item.items.getCount(); j++) {
                                        var subitem=item.items.get(j);
                                        if ( subitem == null ) {
                                            continue;
                                        }

                                        if ( subitem.isValid != null && !subitem.isValid()) {
                                            return false;
                                        }
                                    }                                    
                                }
                                
                            }
                            return true;
                        }
                    });
                    Ung.EditorGrid.prototype.initComponent.call(this);
                },

                rowEditorInputLines: [{
                    xtype:'checkbox',
                    name: "Enable Port Forward Rule",
                    dataIndex: "enabled",
                    fieldLabel: this.i18n._("Enable Port Forward Rule")
                }, {
                    xtype:'textfield',
                    name: "Description",
                    dataIndex: "description",
                    fieldLabel: this.i18n._("Description"),
                    width: 500
                }, {
                    xtype:'fieldset',
                    title: this.i18n._("Rule"),
                    title: "If all of the following conditions are met:",
                    items:[{
                        xtype:'rulebuilder',
                        settingsCmp: this,
                        javaClass: "com.untangle.uvm.network.PortForwardRuleMatcher",
                        anchor:"98%",
                        width: 900,
                        dataIndex: "matchers",
                        matchers: Ung.NetworkUtil.getPortForwardMatchers(this),
                        id:'portForwardBuilder'
                    }]
                }, {
                    xtype: 'fieldset',
                    cls:'description',
                    title: i18n._('Perform the following action(s):'),
                    border: false
                }, {
                    xtype:'textfield',
                    name: "newDestination",
                    allowBlank: false,
                    dataIndex: "newDestination",
                    fieldLabel: this.i18n._("New Destination"),
                    vtype: 'ipAddress'
                }, {
                    xtype:'textfield',
                    name: "newPort",
                    allowBlank: false,
                    dataIndex: "newPort",
                    fieldLabel: this.i18n._("New Port (optional)"),
                    vtype: 'port'
                }]
            });
            
            this.panelPortForwardRules = Ext.create('Ext.panel.Panel',{
                name: 'panelPortForwardRules',
                helpSource: 'network_port_forwards',
                parentId: this.getId(),
                title: this.i18n._('Port Forward Rules'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    title: this.i18n._('Note'),
                    html: this.i18n._(" <b>Port Forward Rules</b>. Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.")
                },  this.gridPortForwardRules]
            });
        },
        // NatRules Panel
        buildNatRules: function() {
            this.gridNatRules = Ext.create( 'Ung.EditorGrid', {
                anchor: '100% -80',
                name: 'NAT Rules',
                settingsCmp: this,
                paginated: false,
                hasReorder: true,
                addAtTop: false,
                emptyRow: {
                    "ruleId": -1,
                    "enabled": true,
                    "auto": true,
                    "description": this.i18n._("[no description]"),
                    "javaClass": "com.untangle.uvm.network.NatRule"
                },
                title: this.i18n._("NAT Rules"),
                recordJavaClass: "com.untangle.uvm.network.NatRule",
                dataProperty:'natRules',
                fields: [{
                    name: 'ruleId'
                }, {
                    name: 'enabled'
                }, {
                    name: 'auto'
                }, {
                    name: 'newSource'
                }, {
                    name: 'matchers'
                },{
                    name: 'description'
                }, {
                    name: 'javaClass'
                }],
                columns: [{
                    header: this.i18n._("Rule Id"),
                    width: 50,
                    dataIndex: 'ruleId',
                    renderer: function(value) {
                        if (value < 0) {
                            return i18n._("new");
                        } else {
                            return value;
                        }
                    }
                }, {
                    xtype:'checkcolumn',
                    header: this.i18n._("Enable"),
                    dataIndex: 'enabled',
                    fixed: true,
                    width:55
                }, {
                    header: this.i18n._("Description"),
                    width: 200,
                    dataIndex: 'description',
                    flex:1
                }],
                columnsDefaultSortable: false,

                initComponent: function() {
                    this.rowEditor = Ext.create('Ung.RowEditorWindow',{
                        grid: this,
                        sizeToComponent: this.settingsCmp,
                        inputLines: this.rowEditorInputLines,
                        rowEditorLabelWidth: 100,
                        populate: function(record, addMode) {
                            return this.populateTree(record, addMode);
                        },
                        // updateAction is called to update the record after the edit
                        updateAction: function() {
                            return this.updateActionTree();
                        },
                        isDirty: function() {
                            if (this.record !== null) {
                                if (this.inputLines) {
                                    for (var i = 0; i < this.inputLines.length; i++) {
                                        var inputLine = this.inputLines[i];
                                        if(inputLine.dataIndex!=null) {
                                            if (this.record.get(inputLine.dataIndex) != inputLine.getValue()) {
                                                return true;
                                            }
                                        }
                                        /* for fieldsets */
                                        if(inputLine.items !=null && inputLine.items.dataIndex != null) {
                                            if (this.record.get(inputLine.items.dataIndex) != inputLine.items.getValue()) {
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                            return Ext.getCmp('natRuleBuilder').isDirty();
                        },
                        isFormValid: function() {
                            if ( Ext.getCmp("newSourceType").getValue() == false ) {
                                if ( Ext.getCmp("newSourceField").getValue() == null ||
                                     Ext.getCmp("newSourceField").getValue() == "" )
                                    return false;
                            }

                            for (var i = 0; i < this.inputLines.length; i++) {
                                var item = null;
                                if ( this.inputLines.get != null ) {
                                    item = this.inputLines.get(i);
                                } else {
                                    item = this.inputLines[i];
                                }
                                if ( item == null ) {
                                    continue;
                                }

                                if ( item.isValid != null) {
                                    if(!item.isValid()) {
                                        return false;
                                    }
                                } else if(item.items !=null && item.items.getCount()>0) {
                                    /* for fieldsets */
                                    for (var j = 0; j < item.items.getCount(); j++) {
                                        var subitem=item.items.get(j);
                                        if ( subitem == null ) {
                                            continue;
                                        }

                                        if ( subitem.isValid != null && !subitem.isValid()) {
                                            return false;
                                        }
                                    }                                    
                                }
                                
                            }
                            return true;
                        }
                    });
                    Ung.EditorGrid.prototype.initComponent.call(this);
                },

                rowEditorInputLines: [{
                    xtype:'checkbox',
                    name: "Enable NAT Rule",
                    dataIndex: "enabled",
                    fieldLabel: this.i18n._("Enable NAT Rule")
                }, {
                    xtype:'textfield',
                    name: "Description",
                    dataIndex: "description",
                    fieldLabel: this.i18n._("Description"),
                    width: 500
                }, {
                    xtype:'fieldset',
                    title: this.i18n._("Rule"),
                    title: "If all of the following conditions are met:",
                    items:[{
                        xtype:'rulebuilder',
                        settingsCmp: this,
                        javaClass: "com.untangle.uvm.network.NatRuleMatcher",
                        anchor:"98%",
                        width: 900,
                        dataIndex: "matchers",
                        matchers: Ung.NetworkUtil.getNatRuleMatchers(this),
                        id:'natRuleBuilder'
                    }]
                }, {
                    xtype: 'fieldset',
                    cls:'description',
                    title: i18n._('Perform the following action(s):'),
                    border: false
                }, {
                    id: "newSourceType",
                    xtype: "combo",
                    name: "auto",
                    allowBlank: false,
                    dataIndex: "auto",
                    fieldLabel: this.i18n._("New Source"),
                    editable: false,
                    store: [[true,i18n._('Auto')], [false,i18n._('Custom')]],
                    valueField: "value",
                    displayField: "displayName",
                    queryMode: 'local',
                    triggerAction: 'all',
                    listClass: 'x-combo-list-small',
                    listeners: {
                        select: Ext.bind(function(combo, ewVal, oldVal) {
                            if (combo.value == true) /* Auto */ {
                                Ext.getCmp('newSourceField').disable();
                                Ext.getCmp('newSourceField').setVisible(false);
                            } else {
                                Ext.getCmp('newSourceField').enable();
                                Ext.getCmp('newSourceField').setVisible(true);
                            }
                        }, this )
                    }
                }, {
                    id: 'newSourceField',
                    xtype:'textfield',
                    name: "newSource",
                    allowBlank: true,
                    dataIndex: "newSource",
                    fieldLabel: this.i18n._("New Source"),
                    hidden: true,
                    vtype: 'ipAddress'
                }],
                
                syncRuleEditorComponents: function () {
                    var natType  = this.query('combo[name="auto"]')[0];
                    var newSource = this.query('textfield[name="newSource"]')[0];

                    newSource.disable();
                    
                    switch(natType.value) {
                      case true:
                        break;
                      case false:
                        newSource.enable();
                        break;
                    }
                    newSource.setVisible(!newSource.disabled); 
                }
            });
            
            this.panelNatRules = Ext.create('Ext.panel.Panel',{
                name: 'panelNatRules',
                helpSource: 'network_nat_rules',
                parentId: this.getId(),
                title: this.i18n._('NAT Rules'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    title: this.i18n._('Note'),
                    html: this.i18n._(" <b>NAT Rules</b>. Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.")
                },  this.gridNatRules]
            });
        },
        // BypassRules Panel
        buildBypassRules: function() {
            this.gridBypassRules = Ext.create( 'Ung.EditorGrid', {
                anchor: '100% -80',
                name: 'Bypass Rules',
                settingsCmp: this,
                paginated: false,
                hasReorder: true,
                addAtTop: false,
                emptyRow: {
                    "ruleId": -1,
                    "enabled": true,
                    "bypass": true,
                    "description": this.i18n._("[no description]"),
                    "javaClass": "com.untangle.uvm.network.BypassRule"
                },
                title: this.i18n._("Bypass Rules"),
                recordJavaClass: "com.untangle.uvm.network.BypassRule",
                dataProperty:'bypassRules',
                fields: [{
                    name: 'ruleId'
                }, {
                    name: 'enabled'
                }, {
                    name: 'bypass'
                }, {
                    name: 'matchers'
                },{
                    name: 'description'
                }, {
                    name: 'javaClass'
                }],
                columns: [{
                    header: this.i18n._("Rule Id"),
                    width: 50,
                    dataIndex: 'ruleId',
                    renderer: function(value) {
                        if (value < 0) {
                            return i18n._("new");
                        } else {
                            return value;
                        }
                    }
                }, {
                    xtype:'checkcolumn',
                    header: this.i18n._("Enable"),
                    dataIndex: 'enabled',
                    fixed: true,
                    width:55
                }, {
                    header: this.i18n._("Description"),
                    width: 200,
                    dataIndex: 'description',
                    flex:1
                }, {
                    xtype:'checkcolumn',
                    header: this.i18n._("Bypass"),
                    dataIndex: 'bypass',
                    fixed: true,
                    width:55
                }],
                columnsDefaultSortable: false,

                initComponent: function() {
                    this.rowEditor = Ext.create('Ung.RowEditorWindow',{
                        grid: this,
                        sizeToComponent: this.settingsCmp,
                        inputLines: this.rowEditorInputLines,
                        rowEditorLabelWidth: 100,
                        populate: function(record, addMode) {
                            return this.populateTree(record, addMode);
                        },
                        // updateAction is called to update the record after the edit
                        updateAction: function() {
                            return this.updateActionTree();
                        },
                        isDirty: function() {
                            if (this.record !== null) {
                                if (this.inputLines) {
                                    for (var i = 0; i < this.inputLines.length; i++) {
                                        var inputLine = this.inputLines[i];
                                        if(inputLine.dataIndex!=null) {
                                            if (this.record.get(inputLine.dataIndex) != inputLine.getValue()) {
                                                return true;
                                            }
                                        }
                                        /* for fieldsets */
                                        if(inputLine.items !=null && inputLine.items.dataIndex != null) {
                                            if (this.record.get(inputLine.items.dataIndex) != inputLine.items.getValue()) {
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                            return Ext.getCmp('bypassRuleBuilder').isDirty();
                        },
                        isFormValid: function() {
                            for (var i = 0; i < this.inputLines.length; i++) {
                                var item = null;
                                if ( this.inputLines.get != null ) {
                                    item = this.inputLines.get(i);
                                } else {
                                    item = this.inputLines[i];
                                }
                                if ( item == null ) {
                                    continue;
                                }

                                if ( item.isValid != null) {
                                    if(!item.isValid()) {
                                        return false;
                                    }
                                } else if(item.items !=null && item.items.getCount()>0) {
                                    /* for fieldsets */
                                    for (var j = 0; j < item.items.getCount(); j++) {
                                        var subitem=item.items.get(j);
                                        if ( subitem == null ) {
                                            continue;
                                        }

                                        if ( subitem.isValid != null && !subitem.isValid()) {
                                            return false;
                                        }
                                    }                                    
                                }
                                
                            }
                            return true;
                        }
                    });
                    Ung.EditorGrid.prototype.initComponent.call(this);
                },

                rowEditorInputLines: [{
                    xtype:'checkbox',
                    name: "Enable Bypass Rule",
                    dataIndex: "enabled",
                    fieldLabel: this.i18n._("Enable Bypass Rule")
                }, {
                    xtype:'textfield',
                    name: "Description",
                    dataIndex: "description",
                    fieldLabel: this.i18n._("Description"),
                    width: 500
                }, {
                    xtype:'fieldset',
                    title: this.i18n._("Rule"),
                    title: "If all of the following conditions are met:",
                    items:[{
                        xtype:'rulebuilder',
                        settingsCmp: this,
                        javaClass: "com.untangle.uvm.network.BypassRuleMatcher",
                        anchor:"98%",
                        width: 900,
                        dataIndex: "matchers",
                        matchers: Ung.NetworkUtil.getBypassRuleMatchers(this),
                        id:'bypassRuleBuilder'
                    }]
                }, {
                    xtype: 'fieldset',
                    cls:'description',
                    title: i18n._('Perform the following action(s):'),
                    border: false
                }, {
                    xtype: "combo",
                    name: "bypass",
                    allowBlank: false,
                    dataIndex: "bypass",
                    fieldLabel: this.i18n._("Bypass"),
                    editable: false,
                    store: [[true,i18n._('Bypass')], [false,i18n._('Capture')]],
                    valueField: "value",
                    displayField: "displayName",
                    queryMode: 'local',
                    triggerAction: 'all',
                    listClass: 'x-combo-list-small'
                }]
            });
            
            this.panelBypassRules = Ext.create('Ext.panel.Panel',{
                name: 'panelBypassRules',
                helpSource: 'network_bypass_rules',
                parentId: this.getId(),
                title: this.i18n._('Bypass Rules'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    title: this.i18n._('Note'),
                    html: this.i18n._(" <b>Bypass Rules</b>. Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.")
                }, this.gridBypassRules]
            });
        },
        // Routes Panel
        buildRoutes: function() {
            var devList = [];
            if( Ung.Util.networkSettings == null ) {
                Ung.Util.networkSettings = main.getNetworkManager().getNetworkSettings();
            }
            for ( var c = 0 ; c < Ung.Util.networkSettings.interfaces.list.length ; c++ ) {
                var intf = Ung.Util.networkSettings.interfaces.list[c];
                var name = "Local on " + intf['systemDev'];
                var key = intf['systemDev'];
                devList.push( [ key, name ] );
            }
            this.gridStaticRoutes = Ext.create('Ung.EditorGrid', {
                anchor: "100% 48%",
                name: 'Static Routes',
                settingsCmp: this,
                emptyRow: {
                    "ruleId": -1,
                    "network": "1.2.3.0",
                    "prefix": 24,
                    "nextHop": "4.3.2.1",
                    "description": this.i18n._("[no description]"),
                    "javaClass": "com.untangle.uvm.network.StaticRoute"
                },
                title: this.i18n._("Static Routes"),
                recordJavaClass: "com.untangle.uvm.network.StaticRoute",
                dataProperty: 'staticRoutes',
                fields: [{
                    name: 'ruleId'
                }, {
                    name: 'network'
                }, {
                    name: 'prefix'
                }, {
                    name: 'nextHop'
                },{
                    name: 'description'
                }, {
                    name: 'javaClass'
                }],
                columns: [{
                    header: this.i18n._("Network"),
                    width: 170,
                    dataIndex: 'network'
                }, {
                    header: this.i18n._("Netmask/Prefix"),
                    width: 170,
                    dataIndex: 'prefix'
                }, {
                    header: this.i18n._("Next Hop"),
                    width: 300,
                    dataIndex: 'nextHop'
                }, {
                    header: this.i18n._("Description"),
                    width: 300,
                    dataIndex: 'description',
                    flex:1
                }],
                sortField: 'network',
                columnsDefaultSortable: true,
                rowEditorInputLines: [, {
                    xtype:'textfield',
                    name: "Description",
                    dataIndex: "description",
                    fieldLabel: this.i18n._("Description"),
                    allowBlank: false,
                    width: 400
                }, {
                    xtype:'textfield',
                    name: "Network",
                    dataIndex: "network",
                    fieldLabel: this.i18n._("Network"),
                    allowBlank: false,
                    vtype:"ipAddress",
                    width: 300
                }, {
                    xtype: "combo",
                    dataIndex: "prefix",
                    fieldLabel: i18n._( "Netmask/Prefix" ),
                    store: Ung.Util.getV4NetmaskList(),
                    valueField: "value",
                    displayField: "displayName",
                    width: 300,
                    listWidth: 70,
                    triggerAction: "all",
                    queryMode: 'local',
                    editable: false
                }, {
                    xtype: "combobox",
                    name: "next_hop",
                    editable : true,
                    allowBlank: false,
                    dataIndex: "nextHop",
                    fieldLabel: i18n._("Next Hop"),
                    editable: true,
                    store: devList,
                    valueField: "value",
                    displayField: "displayName",
                    queryMode: 'local'
                }, {
                    xtype: 'fieldset',
                    cls: 'description',
                    html: this.i18n._("If <b>Next Hop</b> is an IP address that network will routed via the specified IP address.") + "<br/>" +
                        this.i18n._("If <b>Next Hop</b> is an interface that network will be routed <b>locally</b> on that interface.")
                }]
            });

            this.panelRoutes = Ext.create('Ext.panel.Panel',{
                name: 'panelRoutes',
                helpSource: 'network_route_rules',
                parentId: this.getId(),
                title: this.i18n._('Routes'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    title: this.i18n._('Note'),
                    html: this.i18n._(" <b>Static Routes</b> are global routes that control how traffic is routed by destination address. The most specific Static Route is taken for a particular packet, order is not important.")
                }, this.gridStaticRoutes]
            });
        },
        // Advanced Panel
        buildAdvanced: function() {

            this.buildGeneral();
            this.buildQoS();
            this.buildPacketFilter();
            this.buildDnsServer();
            this.buildDnsAdvanced();
            this.buildNetworkCards();

            this.advancedTabPanel = Ext.create('Ext.tab.Panel',{
                activeTab: 0,
                deferredRender: false,
                parentId: this.getId(),
                autoHeight: true,
                flex: 1,
                items: [ this.panelGeneral, this.panelQoS, this.panelPacketFilter, this.panelDnsServer, this.panelDnsAdvanced, this.panelNetworkCards ]
            });
            
            this.panelAdvanced = Ext.create('Ext.panel.Panel',{
                name: 'panelAdvanced',
                helpSource: 'network_advanced',
                parentId: this.getId(),
                title: this.i18n._('Advanced'),
                layout: 'anchor',
                cls: 'ung-panel',
                layout: { type: 'vbox', pack: 'start', align: 'stretch' },
                items: [{
                    xtype: 'fieldset',
                    cls: 'description',
                    flex: 0,
                    html: this.i18n._(" <b>Advanced</b> is for advanced settings. Don't change them. YES THIS MEANS YOU.")
                }, this.advancedTabPanel]
            });
        },
        // General Panel
        buildGeneral: function() {
            this.panelGeneral = Ext.create('Ext.panel.Panel',{
                name: 'panelGeneral',
                helpSource: 'network_general',
                parentId: this.getId(),
                title: this.i18n._('General'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: [{
                    xtype: "checkbox",
                    fieldLabel: this.i18n._("Enable SIP NAT Helper"),
                    labelStyle: 'width:150px',
                    name: 'HostName',
                    checked: this.settings.enableSipNatHelper,
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, newValue) {
                                this.settings.enableSipNatHelper = newValue;
                            }, this)
                        }
                    }
                },{
                    xtype: "checkbox",
                    fieldLabel: this.i18n._("Send ICMP Redirects"),
                    labelStyle: 'width:150px',
                    name: 'DomainName',
                    checked: this.settings.sendIcmpRedirects,
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, newValue) {
                                this.settings.sendIcmpRedirects = newValue;
                            }, this)
                        }
                    }
                }]
            });
        },
        // QoS Panel
        buildQoS: function() {
            this.panelQoS = Ext.create('Ext.panel.Panel',{
                name: 'panelQoS',
                helpSource: 'network_qos',
                parentId: this.getId(),
                title: this.i18n._('QoS'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: []
            });
        },
        // PacketFilter Panel
        buildPacketFilter: function() {
            this.panelPacketFilter = Ext.create('Ext.panel.Panel',{
                name: 'panelPacketFilter',
                helpSource: 'network_packet_filter',
                parentId: this.getId(),
                title: this.i18n._('Packet Filter'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: []
            });
        },
        // DnsServer Panel
        buildDnsServer: function() {
            this.panelDnsServer = Ext.create('Ext.panel.Panel',{
                name: 'panelDnsServer',
                helpSource: 'network_dns_server',
                parentId: this.getId(),
                title: this.i18n._('DNS Server'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: []
            });
        },        
        // DnsAdvanced Panel
        buildDnsAdvanced: function() {
            this.panelDnsAdvanced = Ext.create('Ext.panel.Panel',{
                name: 'panelDnsAdvanced',
                helpSource: 'network_dns_server',
                parentId: this.getId(),
                title: this.i18n._('DNS Advanced'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: []
            });
        },        
        // NetworkCards Panel
        buildNetworkCards: function() {
            this.panelNetworkCards = Ext.create('Ext.panel.Panel',{
                name: 'panelNetworkCards',
                helpSource: 'network_dns_server',
                parentId: this.getId(),
                title: this.i18n._('Network Cards'),
                layout: 'anchor',
                cls: 'ung-panel',
                items: []
            });
        },        
        save: function (isApply) {
            this.saveSemaphore = 1;
            // save language settings
            rpc.networkManager.setNetworkSettings(Ext.bind(function(result, exception) {
                this.afterSave(exception, isApply);
            }, this), this.settings);
        },
        refreshSettings: function() {
            this.settings = rpc.networkManager.getNetworkSettings();
        },
        beforeSave: function(isApply, handler) {
            this.beforeSaveCount = 5;

            Ext.MessageBox.wait(i18n._("Applying Network Settings..."), i18n._("Please wait"));

            this.gridInterfaces.getList(Ext.bind(function(saveList) {
                this.settings.interfaces = saveList;
                this.beforeSaveCount--;
                if (this.beforeSaveCount <= 0)
                    handler.call(this, isApply);
            }, this));

            this.gridPortForwardRules.getList(Ext.bind(function(saveList) {
                this.settings.portForwardRules = saveList;
                this.beforeSaveCount--;
                if (this.beforeSaveCount <= 0)
                    handler.call(this, isApply);
            }, this));

            this.gridNatRules.getList(Ext.bind(function(saveList) {
                this.settings.natRules = saveList;
                this.beforeSaveCount--;
                if (this.beforeSaveCount <= 0)
                    handler.call(this, isApply);
            }, this));

            this.gridBypassRules.getList(Ext.bind(function(saveList) {
                this.settings.bypassRules = saveList;
                this.beforeSaveCount--;
                if (this.beforeSaveCount <= 0)
                    handler.call(this, isApply);
            }, this));

            this.gridStaticRoutes.getList(Ext.bind(function(saveList) {
                this.settings.staticRoutes = saveList;
                this.beforeSaveCount--;
                if (this.beforeSaveCount <= 0)
                    handler.call(this, isApply);
            }, this));
        },
        afterSave: function(exception, isApply) {
            if(Ung.Util.handleException(exception)) return;

            Ung.Util.networkSettings = null; /* clear cached settings object */
            
            this.saveSemaphore--;
            if (this.saveSemaphore == 0) {
                this.refreshSettings();
                if(isApply) {
                    this.clearDirty();
                    Ext.MessageBox.hide();
                } else {
                    Ext.MessageBox.hide();
                    this.closeWindow();
                }
            }
        }
        
    });
}
//@ sourceURL=network.js
