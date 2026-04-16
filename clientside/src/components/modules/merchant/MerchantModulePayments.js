import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox, FileButton } from 'rc-easyui';
import { Panel, Layout, LayoutPanel, Messager, Menu, MenuItem, SwitchButton } from 'rc-easyui';
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
import MerchantModuleMerchantsAccount from './MerchantModuleMerchantsAccount';

class MerchantModulePaymentsC extends React.Component {
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
                beneficiaries: [],
                id:"",
                name: "",
                description: "",
                status: "UNPAID",
                status: "phone",
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
                'name': 'required',
                'status': ['required'],
                "tx_description": ["required"],
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

    getData() {
        this.props.loader("START");
        let searchData = {
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        }
        fetch(common.base_url+"/transactions/getMerchantPayments", {
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

    attemptToStop(row) {
        this.messager.confirm({
            title: "Stop this Payment",
            icon: "info",
            msg: "Are you sure you want to stop this payment?",
            result: (r) => {
                //Continue to submit the form
                if (r) {
                    this.props.loader("START");
                    fetch(common.base_url+"/transactions/stopPayment", {
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
            title: strings.add_payment,
            formdMode: 'new',
            formDialogStateOpened: false,
            formd: {
                beneficiaries: [],
                id:"",
                name: "",
                description: "",
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
            url = common.base_url+"/transactions/editPayment";
        } else {
            url = common.base_url+"/transactions/addPayment";
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
                        //this.resetForm(()=> {
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
                        //});
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


    paymentDetailsDialog() {
        const { tx_details_row, detailedRecordTxDialogStateClosed } = this.state;
        return (
            <Dialog
                title={"Payment Details: "+tx_details_row.name}
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
                            <td><span style={styles.titleText}>Name</span></td>
                            <td>{tx_details_row.name}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Description</span></td>
                            <td style={{width: 500}}>
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
                            <td><span style={styles.titleText}>Payment ID</span></td>
                            <td>{tx_details_row.batch_id}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Status</span></td>
                            <td>{tx_details_row.status}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Total Amount</span></td>
                            <td>{"UGX "+tx_details_row.total_amount}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Created By:</span></td>
                            <td>{tx_details_row.created_by}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Created On:</span></td>
                            <td>{tx_details_row.created_on}</td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Total Paid:</span></td>
                            <td>{tx_details_row.total_paid+"/"+tx_details_row.total_beneficiaries}</td>
                        </tr>
                        
                    </table>
                    <h3>Beneficiaries</h3>
                    <DataGrid
                        ref={ref => this.datagridPaymentDetailsBeneficiaries = ref}
                        style={{ height: 'auto', width:750 }}
                        data={tx_details_row.beneficiaries}>
                        <GridColumn field="rn" align="center" width="30px"
                                cellCss="datagrid-td-rownumber"
                                render={({rowIndex}) => (
                                <span>{rowIndex+1}</span>
                                )}
                            />
                        <GridColumn field="name" 
                            title="Name"></GridColumn>
                        
                        <GridColumn field="account" 
                            title="Account"></GridColumn>

                        <GridColumn field="amount" 
                            title="Amount"></GridColumn>

                        <GridColumn field="beneficiary_status" 
                            title="Status"></GridColumn>
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
                            <span class='pg-subtitles'>Payments | </span>
                            <ComboBox
                                inputId="c1"
                                data={this.state.gridActions}
                                value={this.state.gridActionsValue}
                                onChange={(value) => this.bulkActions(value)}/>
                            <LinkButton 
                                onClick={() => this.addNew()}
                                style={styles.moduleToolBarButtons}
                                iconCls="icon-add">{strings.add_payment}</LinkButton>
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
                    class="f-full"
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
                    <GridColumn field="created_on" title="Created On"></GridColumn>
                    <GridColumn field="name" title="Name"></GridColumn>
                    <GridColumn width={150} field="tx_description" title="Description" ></GridColumn>
                    <GridColumn width={70} title="Paid" render={({ row }) => (
                            <div>
                                {row.total_paid+"/"+row.total_beneficiaries}
                            </div>
                        )}></GridColumn>
                    <GridColumn width={80} field="status" title="status" align="left"></GridColumn>
                    <GridColumn width={130} align="left" field="total_amount" title="Total Amount"></GridColumn>
                    <GridColumn field="note" title="Actions" align="center"
                        render={({ row }) => (
                            <div>
                                <ButtonGroup>
                                    <LinkButton onClick={() => {
                                                this.setState({
                                                    tx_details_row: row,
                                                });
                                                this.dlgRowDetailsRecordTx.open();
                                            }}>{strings.details}</LinkButton>
                                    <LinkButton iconCls="icon-edit" onClick={() => this.editRow(row)}>Edit</LinkButton>
                                </ButtonGroup>

                                {this.returnPaymentAction(row)}
                            </div>
                        )}>

                    </GridColumn>
                </DataGrid>
                {this.paymentDetailsDialog()}
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
            account: "",
            amount: this.state.all_fields_amount,
            account_type: "phone",
            status: "ACTIVE",
            delete: false,
            id: ""
        };

        let formd_ = Object.assign({}, this.state.formd);
        let new_array = [];

        let data = formd_.beneficiaries.slice();
        data.push(new_row);
        //data.unshift({ status: false, _new: true });

        formd_.beneficiaries = data
        this.setState({
            formd: formd_,
        }, () => {
            let last_row = formd_.beneficiaries[(formd_.beneficiaries.length-1)];
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

        let data = formd_.beneficiaries.slice();
        data.push(new_row);
        //data.unshift({ status: false, _new: true });

        formd_.beneficiaries = data
        await this.setState({
            formd: formd_,
        }, () => { });
        let last_row = formd_.beneficiaries[(formd_.beneficiaries.length-1)];
        this.datagrid.beginEdit(last_row);
    }

    removeFormAdminRow() {
        let formd_ = Object.assign({}, this.state.formd);
        let beneficiaries = formd_.beneficiaries;
        //this.datagrid.endEdit();
        if (beneficiaries.length > 0) {
            formd_.beneficiaries = beneficiaries.splice(0, (beneficiaries.length-1));
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

        let data = formd_.beneficiaries.slice();
        //data.push(new_row);
        formd_ = {
            beneficiaries: new_array,
            name: "",
            tx_description: "",
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
        formd_.beneficiaries = new_array
        this.setState({
            formd: formd_,
        }, () => { 
            let last_row = formd_.beneficiaries[(formd_.beneficiaries.length-1)];
            this.datagrid.beginEdit(last_row);
        });
    }

    handleFormChange(name, value) {
        let formd = Object.assign({}, this.state.formd);
        formd[name] = value;
        this.setState({ formd: formd })
    }

    setAllFieldsAmount() {
        let formd_ = Object.assign({}, this.state.formd);
        let data = formd_.beneficiaries.slice();
        for (let i=0; i < data.length; i++) {
            data[i].amount = this.state.all_fields_amount;
        }
        formd_.beneficiaries = data;
        this.setState({
            formd_:formd_
        }, ()=> {
            this.datagrid.endEdit();
        });
    }

    onSelectBenFile() {

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
                                            inputId="name" 
                                            name="name" 
                                            value={row.name} 
                                            style={styles.formDialogFields}></TextBox>
                                    </FormField>
                                    <FormField name="tx_description" label="Description">
                                        <TextBox 
                                            multiline
                                            inputId="tx_description" 
                                            name="tx_description" 
                                            value={row.tx_description} 
                                            style={{width:80, height: 60}}></TextBox>
                                    </FormField>
                                    
                                    <h3>Beneficiaries</h3>
                                    <div style={styles.formDialogLargeWidthAddButtons}>
                                        <ButtonGroup>
                                            <LinkButton onClick={() => {
                                                this.addFormAdminRow();
                                            }}>{strings.add_beneficiary}</LinkButton>
                                            <LinkButton onClick={() => {
                                                this.removeFormAdminRow();
                                                }}>{strings.remove_beneficiary}</LinkButton>
                                            <LinkButton onClick={() => {
                                                this.removeFormAllRows();
                                                }}>{strings.remove_all_rows}</LinkButton>
                                            <FileButton 
                                                autoUpload={true}
                                                onSelect={(files)=>{
                                                    this.props.loader("START");
                                                }}
                                                multiple={true}
                                                onSuccess={(xhr,files) => {
                                                    this.props.loader("STOP");
                                                    console.log(xhr);
                                                    let r = JSON.parse(xhr.xhr.responseText);
                                                    if (r.state == "ERROR") {
                                                        this.props.messager.alert({
                                                            title: "Error",
                                                            icon: "error",
                                                            msg: r.message
                                                        });
                                                    } else if (r.state == "OK") {
                                                        for(let i=0; i < r.data.length; i++) {
                                                            r.data[i].account = r.data[i].account+"";
                                                            this.addFormRowItem(r.data[i]);
                                                        }
                                                    }
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
                                                url={common.base_url+"/transactions/uploadBeneficiariesFile"}
                                                onClick={() => {
                                                this.onSelectBenFile.bind(this)
                                                }}>{strings.upload_excel_file}</FileButton>
                                        </ButtonGroup>
                                        <div style={{float: 'right', marginTop:'-5px'}}>
                                            <TextBox 
                                                placeholder="Amount"
                                                inputId="all_fields_amount" 
                                                name="all_fields_amount" 
                                                value={this.state.all_fields_amount} 
                                                onChange={
                                                    (value) => {
                                                        this.setState({
                                                            all_fields_amount:value
                                                        });
                                                    }
                                                }
                                                style={{width: 150, marginLeft:2}}></TextBox>
                                                <LinkButton 
                                                    style={{margin: "5px"}}
                                                    onClick={() => {
                                                        this.setAllFieldsAmount();
                                                }}>{"Apply Amount"}</LinkButton>
                                        </div>
                                    </div>

                                    <DataGrid
                                        ref={ref => this.datagrid = ref}
                                        style={{ height: 150, width:750 }}
                                        data={row.beneficiaries}
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

                                        <GridColumn field="name" 
                                            title="Name" 
                                            editRules={['required']}
                                            editor={({ row, error }) => (
                                                <Tooltip content={error} tracking>
                                                    <TextBox value={row.name}></TextBox>
                                                </Tooltip>
                                            )}
                                            editable></GridColumn>
                                        
                                        <GridColumn field="account" 
                                            title="Account" 
                                            editRules={{'required':true, "phoneValidation":common.phoneValidation}}
                                            editor={({ row, error }) => (
                                                <Tooltip content={error} tracking>
                                                    <TextBox value={row.account}></TextBox>
                                                </Tooltip>
                                            )}
                                            editable></GridColumn>

                                        <GridColumn field="amount" 
                                            title="Amount" 
                                            editRules={{'required':true, "numericValidation":common.numericValidation}}
                                            editor={({ row, error }) => (
                                                <Tooltip content={error} tracking>
                                                    <TextBox value={row.amount}></TextBox>
                                                </Tooltip>
                                            )}
                                            editable></GridColumn>

                                        <GridColumn field="account_type" 
                                            title="Account Type" 
                                            editable
                                            editor={({ row }) => (
                                                <ComboBox 
                                                    data={this.state.account_type}
                                                    value={row.account_type}></ComboBox>
                                                )}>
                                        </GridColumn>

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
                                            }}>{strings.add_beneficiary}</LinkButton>
                                            <LinkButton onClick={() => {
                                                this.removeFormAdminRow();
                                                }}>{strings.remove_beneficiary}</LinkButton>
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

const MerchantModulePayments = withRouter(MerchantModulePaymentsC);

export default MerchantModulePayments;