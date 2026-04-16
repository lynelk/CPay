import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox, FileButton } from 'rc-easyui';
import { Panel, Layout, LayoutPanel, Messager, Menu, MenuItem, SwitchButton, DateTimeSpinner } from 'rc-easyui';
import { DataGrid, GridColumn, Label, ButtonGroup, SearchBox, Dialog, Tooltip, DateBox } from 'rc-easyui';
import PropTypes from "prop-types";
import { useHistory, withRouter } from "react-router-dom";
import MainMenu from "../../MainMenu";
import common from "../../Common";
import Progress from "../../Progress";
import LinearChart from './LinearChart';
import styles from '../../styles';
import strings from '../../locale';
import { join } from 'path';
import MerchantModuleMerchantsAccount from './MerchantModuleMerchantsAccount';
import DatetimePicker from '../../DatetimePicker';

import ReactExport from "react-export-excel";

const ExcelFile = ReactExport.ExcelFile;
const ExcelSheet = ReactExport.ExcelFile.ExcelSheet;
const ExcelColumn = ReactExport.ExcelFile.ExcelColumn;

class MerchantModuleSmsC extends React.Component {
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
            { value: "cancel", text: "Cancel Selection" }],
            gridActionsValue: "bulk_actions",
            formdMode: 'new',//Can be set to edit
            formd: {
                recipients: [],
                id:"",
                send_time: common.getDefaultDateTime(),
                content: "",
                status: "PENDING",
                ismultiple: false,
            },
            privileges: common.privileges,
            status: [{ value: 'ACTIVE', text: "ACTIVE" },
                { value: 'INACTIVE', text: "INACTIVE" },
                { value: 'SUSPENDED', text: "SUSPENDED" }],
            account_types: [
                { value: 'phone', text: "PHONE NUMBER" },
                { value: 'cpay', text: "Cpay" },
                { value: 'bank', text: "Bank" },
            ],
            rules: {
                'content': 'required',
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
                {value:'recipients',text:'Recipient', iconCls:'icon-iphone'},
                {value:'status',text:'Status', iconCls:'icon-settings'},
                {value:'content',text:'Content', iconCls:'icon-details'}
            ],
            hasAccess: false,
            openMerchantAccount: {},
            tx_details_row:{}, 
            detailedRecordTxDialogStateClosed: true,
            record_tx_data: {
                tx_type: "",
                amount: "",
                description: "",
                balance_type: "",
            },
            record_tx_rules: {
                'amount': {'required':true, "numericValidation":common.numericValidation},
                'tx_type': 'required',
                'description': ['required']
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
            formRecordTxDialogStateClosed: true,
            tx_types: common.tx_types,
            balance_type: common.balance_type,
            available_balances: "",
            tx_details_row:{}, 
            detailedRecordTxDialogStateClosed: true,
            current_balances: [],
            sms_balance: "",
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
                if (user.privileges[i].privilege == "CREATE_BATCH_TX") {
                    isPrivilegeExists = true;
                } else if (user.privileges[i].privilege == "APPROVE_BATCH_TX") {
                    isPrivilegeExists = true;
                } else if (user.privileges[i].privilege == "DOWNLOAD_REPORTS") {
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

    generateBalanceTypesList(balances)
    {
        let bals = [];
        let sms_balance = "";
        for (var i =0; i < balances.length; i++) {
            bals.push({ 
                value: balances[i].balance_type, 
                text: balances[i].code+" ("+common.formatNumber(balances[i].amount)+")", 
            });
            if (balances[i].balance_type == "sms_balance") {
                sms_balance = balances[i].code +" "+common.formatNumber(balances[i].amount);
            }
        }
        this.setState({
            balance_type : bals,
            sms_balance: sms_balance
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
        fetch(common.base_url+"/transactions/getMerchantSms", {
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
                            total: res.data.length,
                            current_balances: res.balances
                        }, () => {
                            this.generateBalanceTypesList(res.balances);
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
                history.push("/portal");
            }
        });
    }

    attemptToStart(row) {
        this.messager.confirm({
            title: "Start this Payment",
            icon: "info",
            msg: "Are you sure you want to start this payment?",
            result: (r) => {
                //Continue to submit the form
                if (r) {
                    this.props.loader("START");
                    fetch(common.base_url+"/transactions/startPayment", {
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
                        if (this.messager != null) {
                            this.messager.alert({
                                title: "Error",
                                icon: "error",
                                msg: error.message
                            });
                        }
                    });
                }
            }
        });
    }

    attemptToCancel(data) {
        
        this.messager.confirm({
            title: "Cancel SMS",
            icon: "info",
            msg: "Are you sure you want to cancel selected SMS(s)?",
            result: (r) => {
                //Continue to submit the form
                if (r) {
                    this.props.loader("START");
                    fetch(common.base_url+"/transactions/cancelSms", {
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
            title: strings.new_sms,
            formdMode: 'new',
            formDialogStateOpened: false,
            formd: {
                recipients: [],
                id:"",
                content: "",
                send_time: common.getDefaultDateTime()
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
            title: "Edit Payment ("+row.name+")"
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
            url = common.base_url+"/transactions/saveSms";
        } else {
            url = common.base_url+"/transactions/saveSms";
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
                        msg: res.message,
                        result: r => {
                            if (r) {
                                this.openOrCloseFormDialog(false);
                            }
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

    bulkActions(value) {
        this.setState({
            gridActionsValue: value
        },() => {
            if (value == "cancel") {
                let data = this.dataGridMain.innerData;
                let selected = [];
                for (let i=0; i < data.length; i++) {
                    if (data[i].selected) {
                        selected.push(data[i]);
                    }
                }
                
                //Now submit Cancel Request
                this.attemptToCancel(selected);

                this.setState({
                    gridActionsValue: "bulk_actions"
                });
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


    paymentDetailsDialog() {
        const { tx_details_row, detailedRecordTxDialogStateClosed } = this.state;
        return (
            <Dialog
                title={"SMS Details: "}
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
                            <td><span style={styles.titleText}>SMS Content</span></td>
                            <td style={{width: 500}}>
                                <div    
                                    style={{width: "100%", overflow: "auto"}}
                                    dangerouslySetInnerHTML={{
                                        __html: (
                                            tx_details_row.content ?
                                            tx_details_row.content.replace(/\n/g, "<BR/>") : ""
                                        )
                                    }}
                                    style={styles.commonBlockText}>
                                
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Total Recipients</span></td>
                            <td>{tx_details_row.total_recipients}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Status</span></td>
                            <td>{tx_details_row.status}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Rate</span></td>
                            <td>{"UGX "+tx_details_row.charge}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Total</span></td>
                            <td>{"UGX "+tx_details_row.total_amount}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Created By:</span></td>
                            <td>{tx_details_row.created_on}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Sent On:</span></td>
                            <td>{tx_details_row.send_time}</td>
                        </tr>
                        
                    </table>
                    <h3>Recipients</h3>
                    <DataGrid
                        ref={ref => this.datagridPaymentDetailsBeneficiaries = ref}
                        style={{ height: 'auto', width:750 }}
                        data={tx_details_row.recipients}>
                        <GridColumn field="rn" align="center" width="30px"
                                cellCss="datagrid-td-rownumber"
                                render={({rowIndex}) => (
                                <span>{rowIndex+1}</span>
                                )}
                            />
                        
                        <GridColumn field="msisdn" 
                            title="Phone number"></GridColumn>

                        <GridColumn field="content" 
                            title="Content"></GridColumn>

                    </DataGrid>
                </div>
                <div className="dialog-button">
                    <LinkButton style={{ width: 80 }} onClick={() => {
                        this.dlgRowDetailsRecordTx.close();
                    }}>{strings.close}</LinkButton>
                </div>
            </Dialog>
        );
    }

    returnPaymentAction(row) {

        if (row.status == "DONE" || row.status == "STOPPED") {
            return ("");
        }

        return (<ButtonGroup style={{marginLeft: 5}}>
            <LinkButton onClick={() => {
            this.attemptToStart(row);
            }}>{
                (row.status == "PENDING" ? "Start" 
                : 
                (row.status == "PROCESSING" ? "Pause" : "Start")
                )
            }</LinkButton> 

            <LinkButton onClick={() => {
                this.attemptToStop(row);
            }}>{"Stop"}</LinkButton>
        </ButtonGroup>);
    }

    recordTransactionRequest() {
        this.props.loader("START");
        let data = this.state.record_tx_data;

        fetch(common.base_url+"/transactions/buySms", {
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
                        this.resetRecordTxForm(()=> {
                            this.dlgRecordTx.close();
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

    resetRecordTxForm(whenDone) {
        this.setState({
            record_tx_data: {
                tx_type: "",
                amount: "",
                description: "",
                balance_type: "",
            },
        }, () => {
            whenDone();
        });
    }

    recordTxSubmit() {
        this.recordTxForm.validate(errors => {
            if (errors != null) {
                return;
            }
            
            this.recordTransactionRequest();
        });
    }

    handleRecordFormChange(name, value) {
        let formd = Object.assign({}, this.state.record_tx_data);
        formd[name] = value;
        this.setState({ record_tx_data: formd });
    }

    recordSmsTxDialog() {
        const { record_tx_data, record_tx_rules, formRecordTxDialogStateClosed, errors } = this.state;
        return (
            <Dialog
                title={strings.buy_sms}
                closed={formRecordTxDialogStateClosed} 
                style={{ width: 500, height: 250 }}
                bodyCls="f-column"
                modal
                onClose={() => {
                    //this.resetRecordTxForm(()=>{});
                }}
                ref={ref => this.dlgRecordTx = ref}>
                <div className="f-full" style={{margin: "5px"}}>
                    <Form
                        ref={ref => this.recordTxForm = ref}
                        model={record_tx_data}
                        rules={record_tx_rules}
                        floatingLabel
                        labelWidth={120}
                        /*labelPosition="top"*/
                        onChange={this.handleRecordFormChange.bind(this)}
                        onValidate={(errors) => this.setState({ errors: errors })}>

                        <FormField name="balance_type" label="Balance Type" >
                            <ComboBox
                                inputId="balance_type"
                                name="balance_type"
                                data={this.state.balance_type}
                                value={this.state.record_tx_data.balance_type}
                                style={styles.formDialogFields}
                                onChange={(value) => this.setState({ value: value })}
                                />
                        </FormField>
                        <FormField name="amount" label="Amount">
                            <TextBox 
                                inputId="amount" 
                                name="amount" 
                                value={record_tx_data.amount} 
                                style={styles.formDialogFields}></TextBox>
                        </FormField>
                    </Form>
                </div>
                <div className="dialog-button">
                    <LinkButton style={{ width: 100 }} onClick={() => {
                        this.recordTxSubmit();
                    }}>{strings.buy_now}</LinkButton>
                    <LinkButton style={{ width: 80 }} onClick={() => {
                        this.dlgRecordTx.close();
                    }}>{strings.close}</LinkButton>
                </div>
            </Dialog>
        );
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
                            <span class='pg-subtitles'>Merchant SMS | </span>
                            <ComboBox
                                inputId="c1"
                                data={this.state.gridActions}
                                value={this.state.gridActionsValue}
                                onChange={(value) => this.bulkActions(value)}/>

                            <LinkButton 
                                onClick={() => this.addNew()}
                                style={styles.moduleToolBarButtons}
                                iconCls="icon-add">{strings.send_sms}</LinkButton>

                            <LinkButton 
                                className="submit-button-red" 
                                style={styles.moduleToolBarButtons}
                                iconCls="icon-money" 
                                onClick={() => {
                                    console.log(this.dataGridMain);
                                    this.dlgRecordTx.open();
                                }}>{strings.buy_sms}</LinkButton>
                            <span> | </span>
                            <strong>
                                {this.state.sms_balance}
                            </strong>
                        </div>
                        <div  style={{float:'right'}}>
                            <SearchBox
                                style={{ width: 250, float:'right' }}
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
                    ref={ref => this.dataGridMain = ref}
                    style={{ height: (windowHeight - common.toReduceGridHeight) }}
                    selectionMode={"multiple"}
                    class="f-full"
                    pagination
                    {...this.state}>
                    <GridColumn width={50} align="center"
                        field="ck"
                        render={({ row }) => (
                            <CheckBox checked={row.selected} onChange={(checked) => this.handleRowCheck(row, checked)}></CheckBox>
                        )}
                        header={() => (
                            <CheckBox 
                                checked={this.state.allChecked} 
                                onChange={(checked) => this.handleAllCheck(checked)}></CheckBox>
                        )}
                        />
                    <GridColumn width={150} field="created_on" title="Created On"></GridColumn>
                    <GridColumn field="content" title="Content" ></GridColumn>
                    <GridColumn width={100} field="status" title="status" align="left"></GridColumn>
                    <GridColumn width={130} align="right" field="total_amount" title="Charge"></GridColumn>
                    <GridColumn width={100} align="right" field="total_recipients" title="No."></GridColumn>
                    <GridColumn width={130} field="note" title="Actions" align="center"
                        render={({ row }) => (
                            <div>
                                <ButtonGroup>
                                    <LinkButton onClick={() => {
                                                this.setState({
                                                    tx_details_row: row,
                                                });
                                                this.dlgRowDetailsRecordTx.open();
                                            }}>{strings.details}</LinkButton>
                                    {/*<LinkButton 
                                        iconCls="icon-edit" 
                                    onClick={() => this.editRow(row)}>Edit</LinkButton>*/}
                                </ButtonGroup>

                                {/*this.returnPaymentAction(row)*/}
                            </div>
                        )}>

                    </GridColumn>
                </DataGrid>
                
                <Messager ref={ref => this.messager = ref}></Messager>

                <PaymentFormDialog 
                    loader={this.props.loader}
                    messager={this.messager}
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
                    {this.searchDialog()}
                    {this.recordSmsTxDialog()}
                    {this.paymentDetailsDialog()}
            </div>
        );
    }
}


class PaymentFormDialog extends React.Component{

    constructor(props) {
        super(props);
        this.state = Object.assign({}, this.props);
        this.state.clickToEdit = true;
        this.state.status = [{ value: 'ACTIVE', text: "ACTIVE" },
        { value: 'INACTIVE', text: "INACTIVE" },
        { value: 'SUSPENDED', text: "SUSPENDED" }];
        this.state.account_type = common.account_types;
        this.state.all_fields_amount = "1000";
        this.state.set_all_fields_amounts = false;
        this.state.pageOptions = {
            layout: ['list', 'sep', 'first', 'prev', 'next', 'last', 'sep', 'refresh', 'sep', 'manual', 'info']
        }
        this.state.ismultiple = false;
        this.state.character_count = 0;
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
            phone: "",
            status: "PENDING",
            conent: "",
            delete: false,
            id: ""
        };

        let formd_ = Object.assign({}, this.state.formd);
        let new_array = [];

        let data = formd_.recipients.slice();
        data.push(new_row);
        //data.unshift({ status: false, _new: true });

        formd_.recipients = data
        this.setState({
            formd: formd_,
        }, () => {
            let last_row = formd_.recipients[(formd_.recipients.length-1)];
            this.datagrid.beginEdit(last_row);
        });
    }

    async addFormRowItem(new_row) {
        /*if (!this.datagrid.endEdit()) {
            return;
        }*/

        //let new_row = item;
        let formd_ = Object.assign({}, this.state.formd);
        let new_array = [];

        let data = formd_.recipients.slice();
        data.push(new_row);
        //data.unshift({ status: false, _new: true });

        formd_.recipients = data
        await this.setState({
            formd: formd_,
        }, () => { });
        let last_row = formd_.recipients[(formd_.recipients.length-1)];
        this.datagrid.beginEdit(last_row);
    }

    removeFormAdminRow() {
        let formd_ = Object.assign({}, this.state.formd);
        let recipients = formd_.recipients;
        //this.datagrid.endEdit();
        if (recipients.length > 0) {
            formd_.recipients = recipients.splice(0, (recipients.length-1));
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

        let data = formd_.recipients.slice();
        //data.push(new_row);
        formd_ = {
            recipients: new_array,
            content: "",
            send_time: common.getDefaultDateTime(),
            ismultiple: false,
            delete: false,
        };

        this.setState({
            formd: formd_
        }, ()=> {
            whenDone();
        });
        
    }

    removeFormAllRows() {
        let formd_ = Object.assign({}, this.state.formd);
        let new_array = [];
        formd_.recipients = new_array
        this.setState({
            formd: formd_,
        }, () => { 
            let last_row = formd_.recipients[(formd_.recipients.length-1)];
            this.datagrid.beginEdit(last_row);
        });
    }

    handleFormChange(name, value) {
        let formd = Object.assign({}, this.state.formd);
        formd[name] = value;
        this.setState({ formd: formd })
    }

    onSelectBenFile() {

    }

    generateFileUploadButton() {
        return (
            <FileButton 
                autoUpload={true}
                onSelect={(files)=>{
                    
                    this.props.loader("START");
                }}
                multiple={false}
                onSuccess={(xhr,files) => {
                    
                    console.log(xhr);
                    let r = JSON.parse(xhr.xhr.responseText);
                    if (r.state == "ERROR") {
                        this.props.messager.alert({
                            title: "Error",
                            icon: "error",
                            msg: r.message
                        });
                    } else if (r.state == "OK") {
                        var content = this.state.formd.content;
                        for(let i=0; i < r.data.length; i++) {
                            r.data[i].phone = r.data[i].phone+"";
                            if (this.state.ismultiple) {
                                var content_ = content;
                                content_ = content_.replace(new RegExp('\{COLB\}', 'i'), r.data[i].cellB);
                                content_ = content_.replace(new RegExp('\{COLC\}', 'i'), 
                                    r.data[i].cellC);
                                content_ = content_.replace(new RegExp('\{COLD\}', 'i'), 
                                    r.data[i].cellD);
                                content_ = content_.replace(new RegExp('\{COLE\}', 'i'), 
                                    r.data[i].cellE);
                                content_ = content_.replace(new RegExp('\{COLF\}', 'i'), 
                                    r.data[i].cellF);
                                
                                r.data[i].content = content_;
                            } else {
                                r.data[i].content = content;
                            }
                            this.addFormRowItem(r.data[i]);
                        }
                    }
                    this.props.loader("STOP");
                }}
                onError={(xhr,files) => {
                    this.props.loader("STOP");
                    console.log(xhr);
                    let r = JSON.parse(xhr.xhr.responseText);
                    if (r.state == "ERROR") {
                        this.props.messager.alert({
                            title: "Error",
                            icon: "error",
                            msg: r.message
                        });
                    }
                }}
                multiple={false}
                url={common.base_url+"/transactions/uploadSmsRecipientsFile"}
                onClick={() => {
                this.onSelectBenFile.bind(this)
                }}>{strings.upload_phones_file}</FileButton>
        );
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
                                    
                                    <FormField name="content" label="SMS Content">
                                        <TextBox 
                                            multiline
                                            inputId="content" 
                                            name="content" 
                                            value={row.content} 
                                            onChange={(text) => {
                                                if (text == null) {
                                                    return;
                                                }
                                                
                                                let count_ = text.length;
                                                this.setState({
                                                    character_count: count_
                                                })
                                            }}
                                            style={{width:380, height: 80}}></TextBox>
                                            
                                            <div style={{margin:"5px"}} width="25px">
                                                <strong>Count: 
                                                    <span style={{color:"#024275"}}>{this.state.character_count}</span>
                                                </strong>
                                            </div>
                                    </FormField>
                                    
                                    <FormField name="multiple" label="Is Personalised?">
                                        <CheckBox 
                                            multiline
                                            inputId="multiple" 
                                            name="multiple" 
                                            value={this.state.ismultiple} 
                                            onChange={(checked)=> {
                                                this.setState({
                                                    ismultiple: checked
                                                });
                                            }}
                                            ></CheckBox>
                                    </FormField>

                                    <FormField name="send_time" label="Send Time">
                                        <DatetimePicker 
                                            inputId="send_time" 
                                            name="send_time" 
                                            value={row.send_time} 
                                            onValueSelected={(value) => {
                                                console.log(value);
                                                this.handleFormChange('send_time', value);
                                            }}
                                            ></DatetimePicker>
                                    </FormField>
                                    
                                    <h3>Phone Numbers</h3>
                                    <div style={styles.formDialogLargeWidthAddButtons}>
                                        <ButtonGroup>
                                            <LinkButton onClick={() => {
                                                this.addFormAdminRow();
                                            }}>{strings.add_phone}</LinkButton>
                                            <LinkButton onClick={() => {
                                                this.removeFormAdminRow();
                                                }}>{strings.remove_phone}</LinkButton>
                                            <LinkButton onClick={() => {
                                                this.removeFormAllRows();
                                                }}>{strings.remove_all_rows}</LinkButton>
                                            {this.generateFileUploadButton()}
                                        </ButtonGroup>
                                    </div>

                                    <DataGrid
                                        ref={ref => this.datagrid = ref}
                                        style={{ height: 150, width:750 }}
                                        data={row.recipients}
                                        clickToEdit={this.state.clickToEdit}
                                        pagination
                                        pageSize={50}
                                        pagePosition={"bottom"}
                                        pageOptions={this.state.pageOptions}
                                        editMode="row">
                                        <GridColumn field="rn" align="center" width="30px"
                                                cellCss="datagrid-td-rownumber"
                                                render={({rowIndex}) => (
                                                <span>{rowIndex+1}</span>
                                                )}
                                            />

                                        <GridColumn field="phone" 
                                            title="Phone" 
                                            width="15%"
                                            editRules={{'required':true, "phoneValidation":common.phoneValidation}}
                                            editor={({ row, error }) => (
                                                <Tooltip content={error} tracking>
                                                    <TextBox value={row.phone}></TextBox>
                                                </Tooltip>
                                            )}
                                            editable></GridColumn>

                                        <GridColumn field="content"
                                            width="70%"
                                            title="Content" 
                                            editRules={{'required':true}}
                                            editor={({ row, error }) => (
                                                <Tooltip content={error} tracking>
                                                    <TextBox value={row.content}></TextBox>
                                                </Tooltip>
                                            )}
                                            editable></GridColumn>

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
                                            }}>{strings.add_phone}</LinkButton>
                                            <LinkButton onClick={() => {
                                                this.removeFormAdminRow();
                                                }}>{strings.remove_phone}</LinkButton>
                                        </ButtonGroup>
                                    </div>
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
                filename="Sms_Log"
                ref={ref => this.excelRef = ref}
                element={<LinkButton 
                    onClick={() => {
                        this.excelRef.download();
                    }}
                    iconCls="icon-excel">{strings.download}</LinkButton>}>
                <ExcelSheet data={this.props.data} name="SMS">
                    <ExcelColumn label="Created On" value="created_on"/>
                    <ExcelColumn label="Sent on" value="send_time"/>
                    <ExcelColumn label="Recipients" value="recipients_string"/>
                    <ExcelColumn label="Status" value="status"/>
                    <ExcelColumn label="Content" value="content"/>
                    <ExcelColumn label="Charges" value="charge"/>
                    <ExcelColumn label="Total Amount" value="total_amount"/>
                </ExcelSheet>
            </ExcelFile>
        );
    }
}

const MerchantModuleSms = withRouter(MerchantModuleSmsC);

export default MerchantModuleSms;