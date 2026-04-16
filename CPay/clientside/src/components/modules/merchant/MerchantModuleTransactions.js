import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox } from 'rc-easyui';
import { Panel, Layout, LayoutPanel, Messager, Menu, MenuItem, SwitchButton, DateBox } from 'rc-easyui';
import { DataGrid, GridColumn, Label, ButtonGroup, SearchBox, Dialog, Tooltip } from 'rc-easyui';
import PropTypes from "prop-types";
import { useHistory, withRouter } from "react-router-dom";
import MainMenu from "../../MainMenu";
import common from "../../Common";
import Progress from "../../Progress";
import LinearChart from './LinearChart';
import styles from '../../styles';
import strings from '../../locale';
import { join } from 'path';

import ReactExport from "react-export-excel";

const ExcelFile = ReactExport.ExcelFile;
const ExcelSheet = ReactExport.ExcelFile.ExcelSheet;
const ExcelColumn = ReactExport.ExcelFile.ExcelColumn;

class MerchantModuleTransactionsC extends React.Component {
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
                status: "ACTIVE",
                account_type: "personal",
                admins:[],
                status: { value: 'ACTIVE', text: "ACTIVE" },
                generate_password: false
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
            searchingValue: {
                value: "",
                category: ""
            },
            search_rules: {
                start_date: common.formatDate(common.getDateMonthsBefore(new Date(), 6)),
                end_date: common.formatDate(new Date()),
                start_date_val : common.getDateMonthsBefore(new Date(), 6),
                end_date_val :new Date(),
                status: "",
                tx_type: ""
            },
            formSearchDialogStateClosed: true,
            search_categories: [
                {value:'all',text:'All Fields',iconCls:'icon-ok'},
                {value:'tx_type',text:'Type', iconCls:'icon-settings'},
                {value:'status',text:'Status', iconCls:'icon-settings'},
                {value:'original_amount',text:'Amount', iconCls:'icon-man'}
            ],
            hasAccess: false,
            tx_details_row:{}, 
            detailedRecordTxDialogStateClosed: true,
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
        let user = localStorage.getItem("merchantUser") != null ? 
            JSON.parse(localStorage.getItem("merchantUser")) : {};

        let isPrivilegeExists = false;

        if (user.privileges) {
            for (let i=0; i < user.privileges.length; i++) {
                if (user.privileges[i].privilege == "ACCESS_TRANSACTION_LOG") {
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
            search_rules: this.state.search_rules,
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        }
        fetch(common.base_url+"/transactions/getMerchantTransactions", {
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
                            total: res.total
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
            } else {
                alert(error);
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

    submitPayment(data) {
        this.messager.confirm({
            title: "Confirm to Initiate inbound Payment",
            icon: "info",
            msg: "Are you sure you want to continue to initiate a mobile money payment on "+data.account+"?",
            result: (r) => {
                //Continue to submit the form
                if (r) {
                    this.props.loader("START");
                    fetch(common.base_url+"/transactions/addPayInTransaction", {
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
                                        this.setState({ formDialogStateOpened: true }, ()=> {
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

    resolve(row) {
        row.generate_password = false;
        let formd = Object.assign({}, row);
        this.setState({ 
            formdMode: 'edit',
            formd: formd,
            formDialogStateOpened: false,
            title: "Edit Merchant ("+row.name+")"
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

    recordTxDetailsDialog() {
        const { tx_details_row, detailedRecordTxDialogStateClosed } = this.state;
        return (
            <Dialog
                title={"Transaction Details: "+tx_details_row.merchant_name}
                closed={detailedRecordTxDialogStateClosed} 
                style={{ width: 850, height: 510 }}
                bodyCls="f-column"
                modal
                onClose={() => {
                    //this.resetRecordTxForm(()=>{});
                }}
                ref={ref => this.dlgRowDetailsRecordTx = ref}>
                <div className="f-full" style={{margin: "5px"}}>
                    <table
                        cellPadding={10} 
                        style={{width: 800}}>
                        <tr>
                            <td><span style={styles.titleText}>Merchant Name</span></td>
                            <td>{tx_details_row.merchant_name}</td>
                        </tr>
                        <tr>
                            <td>
                                <span style={styles.titleText}>Merchant number</span>
                            </td>
                            <td>{tx_details_row.merchant_number}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Gateway ID</span></td>
                            <td>{tx_details_row.gateway_id}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Status</span></td>
                            <td>{tx_details_row.status}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Amount</span></td>
                            <td>{"UGX "+tx_details_row.original_amount_formatted}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Merchant Reference:</span></td>
                            <td>{tx_details_row.tx_merchant_ref}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Network Ref:</span></td>
                            <td>{tx_details_row.tx_gateway_ref}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Payer/Payee Number:</span></td>
                            <td>{tx_details_row.payer_number}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Merchant Description:</span></td>
                            <td>
                                <div    
                                    style={{width: "100%", overflow: "auto"}}
                                    dangerouslySetInnerHTML={{
                                        __html: (
                                            tx_details_row.tx_merchant_description ?
                                            tx_details_row.tx_merchant_description.replace(/\n/g, "<BR/>") : ""
                                        )
                                    }}
                                    style={styles.commonBlockText}>
                                
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Our Description:</span></td>
                            <td>
                                <div    
                                    style={{width: "100%", overflow: "auto"}}
                                    dangerouslySetInnerHTML={{
                                        __html: (
                                            tx_details_row.tx_description ?
                                            tx_details_row.tx_description.replace(/\n/g, "<BR/>") : ""
                                        )
                                    }}
                                    style={styles.commonBlockText}>
                                
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Charges:</span></td>
                            <td>{"UGX "+tx_details_row.charges_formatted}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Created On</span></td>
                            <td>{tx_details_row.created_on}</td>
                        </tr>
                        
                        
                        <tr>
                            <td><span style={styles.titleText}>Callback Trace</span></td>
                            <td style={{width:500}}>
                                <div style={{width: '100%', overflow: "auto"}}
                                    dangerouslySetInnerHTML={{
                                        __html: (
                                            "<PRE>"+
                                            common.encodeHTML(tx_details_row.callback_trace ?
                                            tx_details_row.callback_trace : "")
                                            +"</PRE>"
                                        )
                                    }}
                                    style={styles.commonBlockText}>
                                </div>
                            </td>
                        </tr>
                    </table>
                </div>
                <div className="dialog-button">
                    <LinkButton style={{ width: 80 }} onClick={() => {
                        this.dlgRowDetailsRecordTx.close();
                    }}>{strings.close}</LinkButton>
                </div>
            </Dialog>
        );
    }

    addPayIn() {
        this.setState({
            title: strings.add_payin,
            formdMode: 'new',
            formDialogStateOpened: false,
            formd: {
                id:"",
                account: "",
                description: "",
                amount: "",
            }
        });
    }

    clearSearch_() {
        let search_rules = Object.assign({}, this.state.search_rules);
        search_rules.start_date = common.formatDate(common.getDateMonthsBefore(new Date(), 6));
        search_rules.end_date = common.formatDate(new Date());
        search_rules.start_date_val = common.getDateMonthsBefore(new Date(), 6);
        search_rules.end_date_val = new Date();
        search_rules.status = "";
        search_rules.tx_type = "";
        this.setState({ search_rules: search_rules });
    }

    handleSearchFormChange(name, value) {
        let formd = Object.assign({}, this.state.search_rules);
        if (name=="start_date" || name=="end_date") {
            formd[name] = common.formatDate(value);
            if (name=="start_date") {
                formd["start_date_val"] = value;
            } else if (name=="end_date") {
                formd["end_date_val"] = value;
            }
        } else {
            formd[name] = value;
        }
        this.setState({ search_rules: formd });
    }

    searchDialog() {
        const { search_rules, rules, formSearchDialogStateClosed } = this.state;
        return (
            <Dialog
                title="Search"
                closed={formSearchDialogStateClosed} 
                style={{ width: 450, height: 320 }}
                bodyCls="f-column"
                modal
                ref={ref => this.dlgSearch = ref}>
                
                <div className="f-full" style={{margin: "5px"}}>
                    <Form
                        ref={ref => this.form = ref}
                        model={search_rules}
                        floatingLabel
                        labelWidth={120}
                        /*labelPosition="top"*/
                        onChange={this.handleSearchFormChange.bind(this)}
                        onValidate={(errors) => this.setState({ errors: errors })}>
                    
                        <FormField name="start_date" label="Start Date">
                            <DateBox 
                                format="yyyy-MM-dd"
                                inputId="start_date" 
                                name="start_date" 
                                value={search_rules.start_date_val} 
                                style={styles.formDialogFields}></DateBox>
                        </FormField>
                        <FormField name="end_date" label="End Date">
                            <DateBox 
                                format="yyyy-MM-dd"
                                inputId="end_date" 
                                name="end_date" 
                                value={search_rules.end_date_val} 
                                style={styles.formDialogFields}></DateBox>
                        </FormField>

                        <FormField name="status" label="Status">
                            <TextBox 
                                inputId="status" 
                                name="status" 
                                value={search_rules.status} 
                                style={styles.formDialogFields}></TextBox>
                        </FormField>
                        <FormField name="tx_type" label="Type">
                            <TextBox 
                                inputId="tx_type" 
                                name="tx_type" 
                                value={search_rules.tx_type} 
                                style={styles.formDialogFields}></TextBox>
                        </FormField>
                    </Form>
                </div>
                <div className="dialog-button">
                    <LinkButton style={{ width: 80 }} onClick={() => {
                        this.getData();
                    }}>{strings.go}</LinkButton>
                    <LinkButton style={{ width: 80 }} onClick={() => {
                        this.clearSearch_();
                    }}>{strings.clear}</LinkButton>
                    <LinkButton style={{ width: 80 }} onClick={() => {
                        this.dlgSearch.close();
                    }}>{strings.close}</LinkButton>
                </div>
            </Dialog>
        );
    }


    render () {
        const {searchingValue, search_categories, windowHeight} = this.state;
        
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
                            <span class='pg-subtitles'>Transactions | </span>
                            <ComboBox
                                inputId="c1"
                                data={this.state.gridActions}
                                value={this.state.gridActionsValue}
                                onChange={(value) => this.bulkActions(value)}/>

                            <LinkButton 
                                onClick={() => this.addPayIn()}
                                style={styles.moduleToolBarButtons}
                                iconCls="icon-add">{strings.add_payin}</LinkButton>

                        </div>
                        
                        <div  style={{float:'right'}}>
                            <SearchBox
                                style={{ width: 300, float:'right' }}
                                placeholder={strings.search_merchant}
                                value={searchingValue.value}
                                onSearch={this.handleSearch.bind(this)}
                                category={searchingValue.category}
                                categories={search_categories}
                                addonRight={() => (
                                    <span 
                                        className="textbox-icon icon-clear" 
                                        title={strings.clear_value}
                                        onClick={this.handleClear.bind(this)}></span>
                                )}
                                />
                            <ButtonGroup>
                                <LinkButton 
                                    iconCls="icon-search" 
                                    onClick={() => {
                                        this.dlgSearch.open();
                                    }}>{strings.search}</LinkButton>
                                <Download data={this.state.data} />
                                
                            </ButtonGroup>
                        </div>
                    </Panel>
                </div>
                <DataGrid
                    onRowClick={(row)=> {
                        console.log(this.dataGrid);
                    }}
                    ref={ref => this.dataGrid = ref}
                    style={{ height: (windowHeight - common.toReduceGridHeight) }}
                    selectionMode={"multiple"}
                    pagination={true}
                    total={this.state.total}
                    pageSize={this.state.pageSize}
                    allChecked={this.state.allChecked}
                    rowClicked={this.state.rowClicked}
                    data={this.state.data}
                    pageOptions={this.state.pageOptions}
                    >
                    <GridColumn width={50} align="center"
                        field="ck"
                        render={({ row }) => (
                            <CheckBox checked={row.selected} onChange={(checked) => this.handleRowCheck(row, checked)}></CheckBox>
                        )}
                        header={() => (
                            <CheckBox checked={this.state.allChecked} onChange={(checked) => this.handleAllCheck(checked)}></CheckBox>
                        )}
                        />
                    <GridColumn field="created_on" title="Created on"></GridColumn>
                    <GridColumn field="merchant_id" title="Network ID" 
                        render={({ row }) => (
                            <span>
                                {row.tx_gateway_ref}
                            </span>
                        )}></GridColumn>
                    <GridColumn field="payer_number" title="Payer Number" ></GridColumn>
                    <GridColumn field="status" title="Status" ></GridColumn>
                    <GridColumn field="tx_type" title="Type" align="left"></GridColumn>
                    <GridColumn field="original_amount_formatted" title="Amount" align="right"></GridColumn>
                    <GridColumn field="original_amount" title="Actions" align="center"
                        render={({ row }) => (
                            <div>
                                <ButtonGroup>
                                    <LinkButton iconCls="icon-report" onClick={() => {
                                            //this.resolve(row);
                                            this.setState({
                                                tx_details_row: row, 
                                                /*detailedRecordTxDialogStateClosed: false*/
                                            });
                                            this.dlgRowDetailsRecordTx.open();
                                        }}>Details</LinkButton>
                                </ButtonGroup>
                            </div>
                        )}>

                    </GridColumn>
                </DataGrid>
                {this.searchDialog()}
                {this.recordTxDetailsDialog()}
                <PaymentFormDialog 
                    openOrCloseFormDialog={(state)=> this.openOrCloseFormDialog(state)}
                    parentState={this.state}
                    formd={this.state.formd}
                    title={this.state.title}
                    formDialogStateOpened={this.state.formDialogStateOpened}
                    formdMode={this.state.formdMode}
                    rules={this.state.rules}
                    submitPayment={(data) => {
                        this.submitPayment(data);
                    }}
                    />
                <Messager ref={ref => this.messager = ref}></Messager>
            </div>
        );
    }
}



class PaymentFormDialog extends React.Component{

    constructor(props) {
        super(props);
        this.state = Object.assign({}, this.props);
        this.state.rules = {
            'account': ['required'],
            'tx_description': ['required'],
            "amount": {"required":true,"numericValidation":common.numericValidation},
        };

        this.state.formd = {
            account: "",
            tx_description: "",
            amount: "0",
        };
        this.state.errors = null;
    }

    componentDidMount() {
        
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

    

    saveRow() {
        let formd_ = Object.assign({}, this.state.formd);
        this.form.validate(errors => {
            if (errors != null) {
                return;
            }
            this.props.submitPayment(formd_);
        });
    }

    resetForm(whenDone) {
        let formd_ = Object.assign({}, this.state.formd);
        //data.push(new_row);
        formd_ = {
            account: "",
            tx_description: "",
            amount: "0",
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
        //const row = this.state.formd;
        const { title, formDialogStateOpened } = this.props;
        const {rules, formd } = this.state;
        
        return (
            <Dialog modal 
                title={title} 
                closed={formDialogStateOpened} 
                style={styles.formDialog}
                borderType="none"
                onClose={() => this.props.openOrCloseFormDialog(true)}>
                    <Layout style={{ width: 500, height:'100%', border: '0px #FFFFFF' }}>
                        <LayoutPanel 
                            region="north" 
                            split={false}
                            style={{ height: 320, border: '0px #FFFFFF' }}>
                            
                            <div style={styles.formDialogContainer}>
                                <Form
                                    style={{ width: 400 }}
                                    ref={ref => this.form = ref}
                                    model={formd}
                                    rules={rules}
                                    floatingLabel
                                    labelWidth={300}
                                    labelPosition="top"
                                    onChange={this.handleFormChange.bind(this)}
                                    onValidate={(errors) => this.setState({ errors: errors })}>
                                    
                                    <FormField name="account" label="Account (e.g 256772123456)">
                                        <TextBox 
                                            inputId="account" 
                                            name="account" 
                                            value={formd.account} 
                                            style={styles.formDialogFields}></TextBox>
                                    </FormField>
                                    <FormField name="amount" label="Amount">
                                        <TextBox 
                                            inputId="amount" 
                                            name="amount" 
                                            value={formd.amount} 
                                            style={styles.formDialogFields}></TextBox>
                                    </FormField>
                                    <FormField name="tx_description" label="Description">
                                        <TextBox 
                                            multiline
                                            inputId="tx_description" 
                                            name="tx_description" 
                                            value={formd.tx_description} 
                                            style={{width:290, height: 60}}></TextBox>
                                    </FormField>
                                
                                </Form>
                            </div>
                        </LayoutPanel>
                        <LayoutPanel region="south" style={{ height: 48 }}>
                            <div className="dialog-button">
                                <LinkButton className="submit-button-red" 
                                    iconCls="icon-save" style={{ width: 80 }} 
                                            onClick={() => this.saveRow()}>{strings.submit}</LinkButton>
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

class Download extends React.Component {
    render() {
        return (
            <ExcelFile 
                filename="Account_Transactions"
                ref={ref => this.excelRef = ref}
                element={<LinkButton 
                    onClick={() => {
                        this.excelRef.download();
                    }}
                    iconCls="icon-excel">{strings.download}</LinkButton>}>
                <ExcelSheet data={this.props.data} name="Transactions">
                    <ExcelColumn label="Date time" value="created_on"/>
                    <ExcelColumn label="Network ID" value="tx_gateway_ref"/>
                    <ExcelColumn label="Payer Number" value="payer_number"/>
                    <ExcelColumn label="Status" value="status"/>
                    <ExcelColumn label="Type" value={"tx_type"}/>
                    <ExcelColumn label="Merchant Reference" value="tx_merchant_ref"/>
                    <ExcelColumn label="Description" value="tx_merchant_description"/>
                    <ExcelColumn label="Amount" value="original_amount"/>
                    <ExcelColumn label="Charges" value="charges"/>
                </ExcelSheet>
            </ExcelFile>
        );
    }
}

const MerchantModuleTransactions = withRouter(MerchantModuleTransactionsC);

export default MerchantModuleTransactions;