import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox } from 'rc-easyui';
import { Panel, Layout, LayoutPanel, Messager, Menu, MenuItem, SwitchButton } from 'rc-easyui';
import { DataGrid, GridColumn, Label, ButtonGroup, SearchBox, Dialog, Tooltip } from 'rc-easyui';
import PropTypes from "prop-types";
import { useHistory, withRouter } from "react-router-dom";
import MainMenu from "../MainMenu";
import common from "../Common";
import Progress from "../Progress";
import LinearChart from './LinearChart';
import styles from '../styles';
import strings from '../locale';
import { join } from 'path';
import ModuleMerchantsAccount from './ModuleMerchantsAccount';
import MerchantModuleSettings from './merchant/MerchantModuleSettings';

class ModuleMerchantsC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            total: 0,
            pageSize: 50,
            allChecked: false,
            rowClicked: false,
            data:[],
            pageOptions: {
                layout: ['list', 'sep', 'first', 'prev', 'next', 'last', 'sep', 'refresh', 'sep', 'manual', 'info']
            },
            gridActions: [{ value: "bulk_actions", text: "Bulk Actions" },
            { value: "all", text: "Select All" },
            { value: "clear", text: "Clear Selection" }],
            gridActionsValue: "bulk_actions",
            formdMode: 'new',//Can be set to edit
            formd: {
                id:"",
                name: "",
                short_name:"",
                status: "ACTIVE",
                account_type: "personal",
                admins:[],
                status: { value: 'ACTIVE', text: "ACTIVE" },
                allowed_apis:[],
                generate_password: false,
                generate_new_keys: false
            },
            privileges: common.privileges,
            status: [{ value: 'ACTIVE', text: "ACTIVE" },
                { value: 'INACTIVE', text: "INACTIVE" },
                { value: 'SUSPENDED', text: "SUSPENDED" }],
            account_types: [
                { value: 'personal', text: "PERSONAL" },
                { value: 'business', text: "BUSINESS" },
            ],
            rules: {
                'name': 'required',
                'status': ['required'],
                "email": {"required":true,"emailValidation":common.emailValidation},
                "phone": {'required':true, "phoneValidation":common.phoneValidation}
            },
            errors: {},
            title: '',
            formDialogStateOpened: true,
            statementDialogStateOpened: true,
            searchingValue: {
                value: "",
                category: ""
            },
            categories: [
                {value:'all',text:'All Fields',iconCls:'icon-ok'},
                {value:'account_number',text:'Merchant Account', iconCls:'icon-settings'},
                {value:'status',text:'Status', iconCls:'icon-settings'},
                {value:'account_type',text:'Business Type', iconCls:'icon-settings'},
                {value:'name',text:'Name', iconCls:'icon-settings'}
            ],
            hasAccess: false,
            openMerchantAccount: {},
            merchatSettingsDialogStateClosed: true, 
            selectedMerchantRow: null ,
            windowHeight: window.innerHeight
        };
    }

    handleResize(e) {
        this.setState({ windowHeight: window.innerHeight });
        //console.log(e);
    }

    componentWillMount() {
        window.addEventListener("resize", this.handleResize);
    }

    componentDidMount() {
        
        window.addEventListener("resize", this.handleResize);

        if (this.isUserAllowedAccess()) {
            this.setState({
                hasAccess:true,
            }, ()=> {
                this.getData();
            });
        } else {
            this.messager.alert({
                title: "Access denied!",
                icon: "info",
                msg: "You are not allowed access to this section.",
                result: (r) => {}
            });
        }
    }

    isUserAllowedAccess() {
        let user = localStorage.getItem("user") != null ? 
            JSON.parse(localStorage.getItem("user")) : {};

        let isPrivilegeExists = false;

        if (user.privileges) {
            for (let i=0; i < user.privileges.length; i++) {
                if (user.privileges[i].privilege == "CREATE_ADMIN") {
                    isPrivilegeExists = true;
                } else if (user.privileges[i].privilege == "UPDATE_ADMIN") {
                    isPrivilegeExists = true;
                } else if (user.privileges[i].privilege == "DETETE_ADMIN") {
                    isPrivilegeExists = true;
                }
            }
        }
        
        return isPrivilegeExists;
    }

    resetForm(after) {
        this.setState({
            formd: {
                name: "",
                account_type: "personal",
                admins:[],
                status: 'ACTIVE',
            }
        }, ()=> {
            after();
        });
    }

    getData() {
        this.props.loader("START");
        let searchData = {
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        }
        fetch(common.base_url+"/merchants/getMerchants", {
            method: 'POST', // *GET, POST, PUT, DELETE, etc.
            mode: 'cors', // no-cors, *cors, same-origin
            cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
            credentials: 'include', // include, *same-origin, omit
            headers: {
                'Content-Type': 'application/json',
            },
            redirect: 'follow', // manual, *follow, error
            referrer: 'no-referrer', // no-referrer, *client
            body: JSON.stringify(searchData) // body data type must match "Content-Type" header
        }).then ((response)=>{
            return response.text();
        }).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code == "000") {
                    try {
                        this.setState({
                            data: res.data,
                            total: res.data.length
                        });
                    } catch (ex) {
                        this.messager.alert({
                            title: "Error",
                            icon: "error",
                            msg: ex.message
                        });
                    }
                } else {
                    //If session timed out
                    if (res.code == "107") {
                        this.sessionExpired();
                        return;
                    } else if (res.code == "110") {
                        this.accessNotAllowed(res.message);
                        return;
                    }

                    this.messager.alert({
                        title: "Error "+res.code,
                        icon: "error",
                        msg: res.message
                    });
                }
            } catch(Error) {
                //alert(Error.message);
                this.messager.alert({
                    title: "Error",
                    icon: "error",
                    msg: Error.message
                });
                return;
            }
        }).catch((error) => {
            this.props.loader("STOP");
            if (this.messager != null) {
                this.messager.alert({
                    title: "Error",
                    icon: "error",
                    msg: error.message
                });
            }
        });
    }

    accessNotAllowed(msg) {
        const {history } = this.props;
        this.messager.alert({
            title: "Access denied!",
            icon: "info",
            msg: msg,
            result: (r) => {
                this.setState({
                    hasAccess: false
                });
                //history.goBack();
            }
        });
    }

    sessionExpired() {
        const {history } = this.props;
        this.messager.alert({
            title: "Session Expired!",
            icon: "info",
            msg: "Your are session expired",
            result: (r) => {
                history.push("/");
            }
        });
    }

    deleteRow(row) {
        this.messager.confirm({
            title: "Delete this User",
            icon: "info",
            msg: "Are you sure you want to delete this user?",
            result: (r) => {
                //Continue to submit the form
                if (r) {
                    this.props.loader("START");
                    fetch(common.base_url+"/merchants/deleteMerchant", {
                        method: 'POST', // *GET, POST, PUT, DELETE, etc.
                        mode: 'cors', // no-cors, *cors, same-origin
                        cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
                        credentials: 'include', // include, *same-origin, omit
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        redirect: 'follow', // manual, *follow, error
                        referrer: 'no-referrer', // no-referrer, *client
                        body: JSON.stringify(row) // body data type must match "Content-Type" header
                    }).then ((response)=>{
                        return response.text();
                    }).then((response_) => {
                        this.props.loader("STOP");
                        let res;
                        try {
                            res = JSON.parse(response_);
                            if (res.code == "000") {
                                try {
                                    this.resetForm(()=> {
                                        this.messager.alert({
                                            title: "Success!",
                                            icon: "info",
                                            msg: res.message,
                                            result: r => {
                                                if (r) {
                                                    this.getData();
                                                }
                                            }
                                        });
                                    });
                                } catch (ex) {
                                    this.messager.alert({
                                        title: "Error",
                                        icon: "error",
                                        msg: ex.message
                                    });
                                }
                            } else {
                                //If session timed out
                                if (res.code == "107") {
                                    this.sessionExpired();
                                    return;
                                }

                                this.messager.alert({
                                    title: "Error "+(res.code ? res.code : res.status+" "+res.error),
                                    icon: "error",
                                    msg: res.message+". Error: "+res.error,
                                    result: (r) => {
                                        
                                    }
                                });
                            }
                        } catch(Error) {
                            //alert(Error.message);
                            this.messager.alert({
                                title: "Error",
                                icon: "error",
                                msg: Error.message
                            });
                            return;
                        }
                    }).catch((error) => {
                        this.props.loader("STOP");
                        this.messager.alert({
                            title: "Error",
                            icon: "error",
                            msg: error.message
                        });
                    });
                }
            }
        });
    }

    addNew() {
        //this.openOrCloseFormDialog(false);
        this.setState({
            title: strings.add_merchant,
            formdMode: 'new',
            formDialogStateOpened: false,
            formd: {
                admins: [],
                id:"",
                name: "",
                status: "ACTIVE",
                account_type: "personal",
                admins:[],
                status: { value: 'ACTIVE', text: "ACTIVE" },
                allowed_apis:[],
                generate_password: false,
                generate_new_keys: false
            }
        });
    }

    editRow(row) {
        row.generate_password = false;
        row.generate_new_keys = false;
        let formd = Object.assign({}, row);
        this.setState({ 
            formdMode: 'edit',
            formd: formd,
            formDialogStateOpened: false,
            title: "Edit Merchant ("+row.name+")"
        });
    }

    openAccount(row) {
        this.setState({
            openMerchantAccount: row,
            statementDialogStateOpened: false,
            title: "Merchant ("+row.name+") - "+row.account_number
        });
    }

    handleSearch(searchingValue) {
        this.setState({
            searchingValue: searchingValue
        }, () => {
            this.getData();
        });
    }

    handleClear() {

    }

    saveRow(data) {
        
        let url;
        if (this.state.formdMode == "edit") {
            url = common.base_url+"/merchants/editMerchant";
        } else {
            url = common.base_url+"/merchants/addMerchant";
        }

        //Continue to submit the form
        this.props.loader("START");
        fetch(url, {
            method: 'POST', // *GET, POST, PUT, DELETE, etc.
            mode: 'cors', // no-cors, *cors, same-origin
            cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
            credentials: 'include', // include, *same-origin, omit
            headers: {
                'Content-Type': 'application/json',
            },
            redirect: 'follow', // manual, *follow, error
            referrer: 'no-referrer', // no-referrer, *client
            body: JSON.stringify(data) // body data type must match "Content-Type" header
        }).then ((response)=>{
            return response.text();
        }).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code == "000") {
                    try {
                        this.resetForm(()=> {
                            this.messager.alert({
                                title: "Success!",
                                icon: "info",
                                msg: res.message,
                                result: r => {
                                    if (r) {
                                        this.setState({ formDialogState: true }, ()=> {
                                            this.openOrCloseFormDialog(true);
                                            this.getData();
                                        });
                                    }
                                }
                            });
                        });
                    } catch (ex) {
                        this.messager.alert({
                            title: "Error",
                            icon: "error",
                            msg: ex.message
                        });
                    }
                } else {
                    //If session timed out
                    if (res.code == "107") {
                        this.sessionExpired();
                        return;
                    }

                    this.messager.alert({
                        title: "Error "+(res.code ? res.code : res.status+" "+res.error),
                        icon: "error",
                        msg: res.message+". Error: "+res.error
                    });
                }
            } catch(Error) {
                //alert(Error.message);
                this.messager.alert({
                    title: "Error",
                    icon: "error",
                    msg: Error.message
                });
                return;
            }
        }).catch((error) => {
            this.props.loader("STOP");
            this.messager.alert({
                title: "Error",
                icon: "error",
                msg: error.message
            });
        });
    }

    bulkActions(value) {
        this.setState({
            gridActionsValue: value
        },() => {
            if (value == "all") {
                this.dataGrid.selectRow(0);
            }
        });
    }


    getError(name) {
        const { errors } = this.state;
        if (!errors){
          return null;
        }
        return errors[name] && errors[name].length
            ? errors[name][0]
            : null;
    }

    handleRowCheck(row, checked) {
        let data = this.state.data.slice();
        let index = this.state.data.indexOf(row);
        data.splice(index, 1, Object.assign({}, row, { selected: checked }));
        let checkedRows = data.filter(row => row.selected);
        this.setState({
            allChecked: data.length === checkedRows.length,
            rowClicked: true,
            data: data
        }, () => {
            this.setState({ rowClicked: false })
        });
    }

    handleAllCheck(checked) {
        if (this.state.rowClicked) {
            return;
        }
        let data = this.state.data.map(row => {
            return Object.assign({}, row, { selected: checked })
        });
        this.setState({
            allChecked: checked,
            data: data
        });
    }

    /*
    * Set state to true to close otherwise false to oepn
    */
    openOrCloseFormDialog(state) {
        this.setState({
            formDialogStateOpened: state
        });
    }

    openOrCloseStatementDialog(state) {
        this.setState({
            statementDialogStateOpened: state
        });
    }

    dialogMerchantSettings() {
        const { merchatSettingsDialogStateClosed, selectedMerchantRow } = this.state;
        let merchantSettingsContent = (merchatSettingsDialogStateClosed ? null :
            <MerchantModuleSettings
                sessionExpired={this.props.sessionExpired}
                logOut={this.props.logoutUser}
                loader={this.props.loader}
                merchant_id={selectedMerchantRow.id} />
            );
        return (

            <Dialog
                title={"Merchant Settings: "+(selectedMerchantRow ? selectedMerchantRow.name : "")}
                closed={merchatSettingsDialogStateClosed} 
                onClose={() => {
                    this.setState({
                        merchatSettingsDialogStateClosed: true
                    })
                }}
                style={{ width: 800, height: 550 }}
                bodyCls="f-column"
                modal
                ref={ref => this.dlgMerchantSettings = ref}>
                
                <div className="f-full" style={{margin: "5px"}}>
                    {merchantSettingsContent}
                </div>

                <div className="dialog-button">
                    <LinkButton style={{ width: 80 }} onClick={() => {
                        this.dlgMerchantSettings.close();
                    }}>{strings.close}</LinkButton>
                </div>
            </Dialog>
        );
    }


    render () {
        const {searchingValue, categories, windowHeight} = this.state;
        if (!this.state.hasAccess) {
            return (<div>
                <Messager ref={ref => this.messager = ref}></Messager>
            </div>);
        }

        return (
            <div>
                <div>
                    <Panel bodyStyle={{ padding: '5px'}}>
                        <div style={{float:'left'}}>
                            <ComboBox
                                inputId="c1"
                                data={this.state.gridActions}
                                value={this.state.gridActionsValue}
                                onChange={(value) => this.bulkActions(value)}/>
                            <LinkButton 
                                onClick={() => this.addNew()}
                                style={styles.moduleToolBarButtons}
                                iconCls="icon-add">{strings.add_merchant}</LinkButton>
                        </div>
                        <SearchBox
                            style={{ width: 300, float:'right' }}
                            placeholder={strings.search_merchant}
                            value={searchingValue.value}
                            onSearch={this.handleSearch.bind(this)}
                            category={searchingValue.category}
                            categories={categories}
                            addonRight={() => (
                                <span 
                                    className="textbox-icon icon-clear" 
                                    title={strings.clear_value}
                                    onClick={this.handleClear.bind(this)}></span>
                            )}
                            />
                    </Panel>
                </div>
                <DataGrid
                    ref={ref => this.dataGrid = ref}
                    style={{ height: (windowHeight - common.toReduceGridHeight) }}
                    selectionMode={"multiple"}
                    pagination
                    {...this.state}>
                    <GridColumn width={50} align="center"
                        field="ck"
                        render={({ row }) => (
                            <CheckBox checked={row.selected} onChange={(checked) => this.handleRowCheck(row, checked)}></CheckBox>
                        )}
                        header={() => (
                            <CheckBox checked={this.state.allChecked} onChange={(checked) => this.handleAllCheck(checked)}></CheckBox>
                        )}
                        />
                    <GridColumn field="name" title="Name"></GridColumn>
                    <GridColumn width={90} field="short_name" title="Short Name"></GridColumn>
                    <GridColumn width={80} field="account_number" title="Account"></GridColumn>
                    <GridColumn width={100} field="account_type" title="Type" ></GridColumn>
                    <GridColumn width={100} field="created_by" title="Created By" ></GridColumn>
                    <GridColumn width={80} field="status" title="status" align="left"></GridColumn>
                    <GridColumn width={300} field="note" title="Actions" align="center"
                        render={({ row }) => (
                            <div>
                                <ButtonGroup>
                                    <LinkButton onClick={() => {
                                                this.openAccount(row);
                                            }}>{strings.account}</LinkButton>
                                    <LinkButton
                                        iconCls="icon-settings" 
                                        onClick={() => {
                                                this.setState({
                                                    merchatSettingsDialogStateClosed: false, 
                                                    selectedMerchantRow: row 
                                                })
                                            }}>{"Settings"}</LinkButton>
                                    <LinkButton iconCls="icon-edit" onClick={() => this.editRow(row)}>Edit</LinkButton>
                                    <LinkButton iconCls="icon-remove" onClick={() => this.deleteRow(row)}>Delete</LinkButton>
                                </ButtonGroup>
                            </div>
                        )}>

                    </GridColumn>
                </DataGrid>
                <Messager ref={ref => this.messager = ref}></Messager>
                <MerchantFormDialog 
                    openOrCloseFormDialog={(state)=> this.openOrCloseFormDialog(state)}
                    parentState={this.state}
                    formd={this.state.formd}
                    title={this.state.title}
                    formDialogStateOpened={this.state.formDialogStateOpened}
                    formdMode={this.state.formdMode}
                    rules={this.state.rules}
                    addFormAdminRow={() => this.addFormAdminRow(this)}
                    saveRow={(data) => {
                        this.saveRow(data);
                    }}
                    />

                <ModuleMerchantsAccount 
                    openOrCloseStatementDialog={(state)=> this.openOrCloseStatementDialog(state)}
                    title={this.state.title}
                    loader={(state) => {this.props.loader(state)}}
                    sessionExpired={this.sessionExpired}
                    accessNotAllowed={this.accessNotAllowed.bind(this)}
                    messager={this.messager}
                    statementDialogStateOpened={this.state.statementDialogStateOpened}
                    openMerchantAccount={this.state.openMerchantAccount}
                    />

                    {this.dialogMerchantSettings()}
            </div>
        );
    }
}


class MerchantFormDialog extends React.Component{

    constructor(props) {
        super(props);
        this.state = Object.assign({}, this.props);
        this.state.clickToEdit = true;
        this.state.generate_new_keys = false;
        this.state.status = [{ value: 'ACTIVE', text: "ACTIVE" },
        { value: 'INACTIVE', text: "INACTIVE" },
        { value: 'SUSPENDED', text: "SUSPENDED" }];
        this.state.account_types = [
            { value: 'personal', text: "PERSONAL" },
            { value: 'business', text: "BUSINESS" },
        ];
        this.state.allowed_apis = common.allowed_apis;
        this.state.privileges = common.merchant_privileges;
    }

    componentDidMount() {
        /*this.setState({
            formd: this.props.formd
        }, ()=> {
            
        });*/
    }

    shouldComponentUpdate(nextProps, nextState) {
        if (this.props.formd != nextProps.formd) {
            if (this.props.formdMode == "new") {
                this.resetForm(() => {});
            }
            this.setState({
                formd: nextProps.formd
            }, ()=> {

            });
        }
        return true;
    }

    addFormAdminRow() {
        /*if (!this.datagrid.endEdit()) {
            return;
        }*/

        let new_row = {
            name: "",
            email: "",
            phone: "",
            status: "ACTIVE",
            privileges: [],
            generate_pw: false,
            delete: false,
            id: ""
        };

        let formd_ = Object.assign({}, this.state.formd);
        let new_array = [];

        let data = formd_.admins.slice();
        data.push(new_row);
        //data.unshift({ status: false, _new: true });

        formd_.admins = data
        this.setState({
            formd: formd_,
        }, () => {
            let last_row = formd_.admins[(formd_.admins.length-1)];
            this.datagrid.beginEdit(last_row);
        });
    }

    removeFormAdminRow() {
        let formd_ = Object.assign({}, this.state.formd);
        let admins = formd_.admins;
        //this.datagrid.endEdit();
        if (admins.length > 0) {
            formd_.admins = admins.splice(0, (admins.length-1));
            this.setState({
                formd: formd_
            }, () => {
                this.datagrid.cancelEdit(); 
            });
        }
    }

    saveRow() {
        let formd_ = Object.assign({}, this.state.formd);
        this.props.saveRow(formd_);
    }

    resetForm(whenDone) {
        let formd_ = Object.assign({}, this.state.formd);
        let new_array = [];

        let data = formd_.admins.slice();
        //data.push(new_row);
        formd_ = {
            admins: new_array,
            id:"",
            name: "",
            short_name: "",
            status: "ACTIVE",
            account_type: "personal",
            privileges: common.merchant_privileges,
            allowed_apis: [],
            admins:[],
            status: 'ACTIVE',
            generate_pw: false,
            generate_new_keys: false,
            delete: false,
        };

        this.setState({
            formd: formd_
        }, ()=> {
            whenDone();
        });
        
    }

    handleFormChange(name, value) {
        let formd = Object.assign({}, this.state.formd);
        formd[name] = value;
        this.setState({ formd: formd })
    }

    render() {
        //console.log(this.state.formd);
        const row = this.state.formd;
        const { title, formDialogStateOpened, rules } = this.props;
        
        return (
            <Dialog modal 
                title={title} 
                closed={formDialogStateOpened} 
                style={styles.formDialogLargeWidth}
                borderType="none"
                onClose={() => this.props.openOrCloseFormDialog(true)}>
                    <Layout style={{ width: 800, height:'100%', border: '0px #FFFFFF' }}>
                        <LayoutPanel 
                            region="north" 
                            split={false}
                            style={{ height: 320, border: '0px #FFFFFF' }}>
                            <div style={styles.formDialogContainer}>
                                <Form
                                    style={{ width: 700 }}
                                    ref={ref => this.form = ref}
                                    model={row}
                                    rules={rules}
                                    floatingLabel
                                    labelWidth={300}
                                    /*labelPosition="top"*/
                                    onChange={this.handleFormChange.bind(this)}
                                    onValidate={(errors) => this.setState({ errors: errors })}>
                                    
                                    <FormField name="name" label="Name">
                                        <TextBox 
                                            iconCls="icon-man"
                                            inputId="name" 
                                            name="name" 
                                            value={row.name} 
                                            style={styles.formDialogFields}></TextBox>
                                    </FormField>
                                    <FormField name="short_name" label="Short Name">
                                        <TextBox 
                                            iconCls="icon-man"
                                            inputId="short_name" 
                                            name="short_name" 
                                            value={row.short_name} 
                                            style={styles.formDialogFields}></TextBox>
                                    </FormField>
                                    <FormField name="status" label="Status">
                                        <ComboBox 
                                            inputId="status" 
                                            name="status" 
                                            data={this.state.status}
                                            value={row.status} 
                                            style={styles.formDialogFields}></ComboBox>
                                    </FormField>
                                    <FormField name="account_type" label="Account Type">
                                        <ComboBox 
                                            inputId="account_type" 
                                            name="account_type" 
                                            data={this.state.account_types}
                                            value={row.account_type} 
                                            style={styles.formDialogFields}></ComboBox>
                                    </FormField>
                                    <FormField name="allowed_apis" label="Allowed APIs Access" >
                                        <ComboBox 
                                            inputId="allowed_apis" 
                                            name="allowed_apis" 
                                            multiple
                                            data={common.allowed_apis}
                                            value={row.allowed_apis}
                                            style={styles.formDialogFields}></ComboBox>
                                    </FormField>
                                    <FormField name="generate_new_keys" label="Generate New Keys">
                                        <CheckBox 
                                            inputId="generate_new_keys" 
                                            name="generate_new_keys" 
                                            value={row.generate_new_keys} 
                                            checked={row.generate_new_keys}></CheckBox>
                                    </FormField>

                                    <FormField name="private_key" label="Private key">
                                        <TextBox 
                                            multiline
                                            inputId="private_key" 
                                            name="private_key" 
                                            value={row.private_key} 
                                            style={{width:200, height: 300}}></TextBox>
                                    </FormField>

                                    <FormField name="public_key" label="Public key">
                                        <TextBox 
                                            multiline
                                            inputId="public_key" 
                                            name="public_key" 
                                            value={row.public_key} 
                                            style={{width:200, height: 300}}></TextBox>
                                    </FormField>
                                    
                                    <h3>Merchant Admins</h3>
                                    <DataGrid
                                        ref={ref => this.datagrid = ref}
                                        style={{ height: 200, width:850 }}
                                        data={row.admins}
                                        clickToEdit={this.state.clickToEdit}
                                        editMode="row">
                                        <GridColumn field="rn" align="center" width="30px"
                                                cellCss="datagrid-td-rownumber"
                                                render={({rowIndex}) => (
                                                <span>{rowIndex+1}</span>
                                                )}
                                            />
                                        <GridColumn field="name" 
                                            title="Name" 
                                            editRules={['required']}
                                            editor={({ row, error }) => (
                                                <Tooltip content={error} tracking>
                                                    <TextBox value={row.name}></TextBox>
                                                </Tooltip>
                                            )}
                                            editable></GridColumn>
                                        <GridColumn field="email" 
                                            title="Email" 
                                            editRules={{'required':true, "emailValidation":common.emailValidation}}
                                            editor={({ row, error }) => (
                                                <Tooltip content={error} tracking>
                                                    <TextBox value={row.email}></TextBox>
                                                </Tooltip>
                                            )}
                                            editable></GridColumn>
                                        <GridColumn field="phone" 
                                            title="Phone" 
                                            editRules={{'required':true, "phoneValidation":common.phoneValidation}}
                                            editor={({ row, error }) => (
                                                <Tooltip content={error} tracking>
                                                    <TextBox value={row.phone}></TextBox>
                                                </Tooltip>
                                            )}
                                            editable></GridColumn>
                                        <GridColumn field="status" 
                                            title="Status" 
                                            editable
                                            editor={({ row }) => (
                                                <ComboBox 
                                                    data={this.state.status}
                                                    value={row.status}></ComboBox>
                                                )}>
                                        </GridColumn>
                                        <GridColumn field="privileges" 
                                            width={150}
                                            title="Privileges" 
                                            editable
                                            editor={({ row }) => (
                                                <ComboBox 
                                                    multiple
                                                    data={this.state.privileges}
                                                    value={row.privileges}></ComboBox>
                                                )}>
                                        </GridColumn>
                                        <GridColumn field="generate_pw" 
                                            title={strings.generate_password} 
                                            align="center"
                                            editable
                                            editor={({ row }) => (
                                                <CheckBox checked={row.generate_pw}></CheckBox>
                                            )}
                                            render={({ row }) => (
                                                <span>{row.generate_pw ? "Yes" : "No"}</span>
                                            )}
                                        />
                                        <GridColumn field="delete" 
                                            title={strings.delete} 
                                            align="center"
                                            editable
                                            editor={({ row }) => (
                                                <CheckBox checked={row.delete}></CheckBox>
                                            )}
                                            render={({ row }) => (      
                                                <span>{row.delete ? "Yes" : "No"}</span>
                                            )}
                                        />
                                    </DataGrid>
                                    <div style={styles.formDialogLargeWidthAddButtons}>
                                        <ButtonGroup>
                                            <LinkButton onClick={() => {
                                                this.addFormAdminRow();
                                            }}>{strings.add_merchant}</LinkButton>
                                            <LinkButton onClick={() => {
                                                this.removeFormAdminRow();
                                                }}>{strings.delete_merchant}</LinkButton>
                                        </ButtonGroup>
                                    </div>
                                </Form>
                            </div>
                        </LayoutPanel>
                        <LayoutPanel region="south" style={{ height: 48 }}>
                            <div className="dialog-button">
                                <LinkButton className="submit-button-red" 
                                    iconCls="icon-save" style={{ width: 80 }} 
                                    onClick={() => this.saveRow()}>Save</LinkButton>
                                <LinkButton iconCls="icon-cancel" style={{ width: 80 }} 
                                    onClick={() => {
                                        this.resetForm(() => {
                                            this.props.openOrCloseFormDialog(true);
                                        });
                                    }}>Close</LinkButton>
                            </div>
                        </LayoutPanel>
                    </Layout>
            </Dialog>
        );
    }
}

const ModuleMerchants = withRouter(ModuleMerchantsC);

export default ModuleMerchants;