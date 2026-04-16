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

class ModuleTransactionsC extends React.Component {
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
            search_categories: [
                {value:'all',text:'All Fields',iconCls:'icon-ok'},
                {value:'tx_type',text:'Type', iconCls:'icon-settings'},
                {value:'status',text:'Status', iconCls:'icon-settings'},
                {value:'original_amount',text:'Amount', iconCls:'icon-man'},
                {value:'merchant_id',text:'Merchant ID', iconCls:'icon-man'}
            ],
            hasAccess: false,
            tx_details_row:{}, 
            detailedRecordTxDialogStateClosed: true,
            title_resolve:"Resolve Transaction", 
            row_resolve_form: {
                tx_gateway_ref:"",
                resolve_status:"",
                id:"",
            },
            rules_resolve:{
                'resolve_status': 'required',
                'tx_gateway_ref': ['required'],
            },
            formResolveDialogState: true,
            resolve_status: [
                { value: 'SUCCESSFUL', text: "SUCCESSFUL" },
                { value: 'FAILED', text: "FAILED" },
            ],
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
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        }
        fetch(common.base_url+"/transactions/getTransactions", {
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

    deleteRow(row) {
        this.messager.confirm({
            title: "Delete this User",
            icon: "info",
            msg: "Are you sure you want to delete this user?",
            result: (r) => {
                //Continue to submit the form
                if (r) {
                    this.props.loader("START");
                    fetch(common.base_url+"/transactions/deleteMerchant", {
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
                                    msg: res.message,
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


    resolveTransaction(row) {
        this.messager.confirm({
            title: "Resolve Transaction",
            icon: "info",
            msg: "Are you sure you want to resolve this transaction to "+row.resolve_status+"?",
            result: (r) => {
                //Continue to submit the form
                if (r) {
                    this.resolveDialog.close();
                    this.props.loader("START");
                    fetch(common.base_url+"/transactions/resolveTransaction", {
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
                                    msg: res.message,
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

    handleFormChangeResolve(name, value) {
        
        let row_resolve_form = Object.assign({}, this.state.row_resolve_form);
        row_resolve_form[name] = value;
        this.setState({ row_resolve_form: row_resolve_form })
        
    }

    renderResolveDialog() {
        const row_resolve_form = this.state.row_resolve_form;
        const { title_resolve, formResolveDialogState, rules_resolve } = this.state;
        return (
            <Dialog modal 
                title={title_resolve} 
                closed={formResolveDialogState} 
                style={{width: 500, height: 250}}
                ref={ref => this.resolveDialog = ref}
                borderType="none"
                onOpen={() => {
                    
                }}
                onClose={() => this.setState({ formResolveDialogState: true })}>
                    <Layout style={{ width: 500, height:'100%', border: '0px #FFFFFF' }}>
                        <LayoutPanel 
                            region="north" 
                            split={false}
                            style={{ height: 170, border: '0px #FFFFFF' }}>
                            <div style={styles.formDialogContainer}>
                                <Form
                                    ref={ref => this.form_resolve = ref}
                                    model={row_resolve_form}
                                    rules={rules_resolve}
                                    floatingLabel
                                    labelWidth={120}
                                    /*labelPosition="top"*/
                                    onChange={this.handleFormChangeResolve.bind(this)}
                                    onValidate={(errors) => this.setState({ errors: errors })}>
                                    
                                    <FormField name="tx_gateway_ref" label="Nework Ref">
                                        <TextBox 
                                            iconCls="icon-man"
                                            inputId="tx_gateway_ref" 
                                            name="tx_gateway_ref" 
                                            value={row_resolve_form.tx_gateway_ref} 
                                            style={styles.formDialogFields}></TextBox>
                                    </FormField>
                                    
                                    <FormField name="resolve_status" label="Resolve to Status" >
                                        <ComboBox
                                            inputId="resolve_status"
                                            name="resolve_status"
                                            data={this.state.resolve_status}
                                            value={row_resolve_form.resolve_statuses}
                                            style={styles.formDialogFields}
                                            onChange={(value) => {
                                                this.setState({ 
                                                    value: value 
                                                })
                                            }}
                                            />
                                    </FormField>
                                </Form>
                            </div>
                        </LayoutPanel>
                        <LayoutPanel region="south" style={{ height: 48 }}>
                            <div className="dialog-button">
                                <LinkButton className="submit-button-red" 
                                    iconCls="icon-save" style={{ width: 80 }} 
                                    onClick={() => {
                                        this.resolveTransaction(this.state.row_resolve_form);
                                    }}>Submit</LinkButton>
                                <LinkButton iconCls="icon-cancel" style={{ width: 80 }} 
                                    onClick={() => {
                                        this.resolveDialog.close()
                                    }}>Close</LinkButton>
                            </div>
                        </LayoutPanel>
                    </Layout>
            </Dialog>
        );
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
                            <td><span style={styles.titleText}>Request Trace</span></td>
                            <td style={{width: 500}}>
                                <div    
                                    style={{width: "100%", overflow: "auto"}}
                                    dangerouslySetInnerHTML={{
                                        __html: ("<PRE>"+
                                            common.encodeHTML(tx_details_row.tx_request_trace ?
                                            tx_details_row.tx_request_trace : "")
                                           +"</PRE>"
                                        )
                                    }}
                                    style={styles.commonBlockText}>
                                
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td><span style={styles.titleText}>Updated Trace</span></td>
                            <td style={{width:500}}>
                                <div style={{width: '100%', overflow: "auto"}}
                                    dangerouslySetInnerHTML={{
                                        __html: ( "<PRE>"+
                                            common.encodeHTML(tx_details_row.tx_update_trace ?
                                            tx_details_row.tx_update_trace: "")
                                            +"</PRE>"
                                            )
                                    }}
                                    style={styles.commonBlockText}>
                                </div>
                            </td>
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
                    
                    {this.displayResolve(tx_details_row)}

                    <LinkButton style={{ width: 80 }} onClick={() => {
                        this.dlgRowDetailsRecordTx.close();
                    }}>{strings.close}</LinkButton>
                </div>
            </Dialog>
        );
    }

    displayResolve(tx_details_row) {
        if (tx_details_row.status == "SUCCESSFUL" || tx_details_row.status == "FAILED") {
            return (<di></di>);
        } else {
            return (
                <LinkButton style={{ width: 80 }} onClick={() => {
                    let row_resolve_form = this.state.row_resolve_form;
                    row_resolve_form.id = tx_details_row.id;
                    this.setState({
                        row_resolve_form: row_resolve_form
                    }, () => {
                        this.resolveDialog.open()
                    })
                }}>{strings.resolve}</LinkButton>
            );
        }
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
                            <ComboBox
                                inputId="c1"
                                data={this.state.gridActions}
                                value={this.state.gridActionsValue}
                                onChange={(value) => this.bulkActions(value)}/>
                        </div>
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
                    <GridColumn field="created_on" title="Created on"></GridColumn>
                    <GridColumn field="merchant_id" title="Merchant ID" 
                        render={({ row }) => (
                            <span>
                                {row.merchant_name}
                                {/*<Tooltip content={row.merchant_name+" "+row.merchant_number+")"}>
                                    {row.merchant_name}    
                                </Tooltip>*/}
                            </span>
                        )}></GridColumn>
                    <GridColumn field="payer_number" title="Payer Number" ></GridColumn>
                    <GridColumn field="tx_merchant_ref" title="Merchant Ref" ></GridColumn>
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
                {this.recordTxDetailsDialog()}
                {this.renderResolveDialog()}
                <Messager ref={ref => this.messager = ref}></Messager>
            </div>
        );
    }
}

const ModuleTransactions = withRouter(ModuleTransactionsC);

export default ModuleTransactions;