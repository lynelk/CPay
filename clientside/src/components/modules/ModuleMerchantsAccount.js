import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox } from 'rc-easyui';
import { Panel, Layout, LayoutPanel, Messager, Menu, MenuItem, SwitchButton } from 'rc-easyui';
import { DataGrid, GridColumn, Label,DateBox, ButtonGroup, SearchBox, Dialog, Tooltip } from 'rc-easyui';
import PropTypes from "prop-types";
import { useHistory, withRouter } from "react-router-dom";
import MainMenu from "../MainMenu";
import common from "../Common";
import Progress from "../Progress";
import LinearChart from './LinearChart';
import styles from '../styles';
import strings from '../locale';

import ReactExport from "react-export-excel";
const ExcelFile = ReactExport.ExcelFile;
const ExcelSheet = ReactExport.ExcelFile.ExcelSheet;
const ExcelColumn = ReactExport.ExcelFile.ExcelColumn;

/**
 * 
 * Props: 
 * - loader()
 * - sessionExpired()
 * - accessNotAllowed()
 * - messager
 * - openOrCloseStatementDialog
 */

class ModuleMerchantAccouunt extends React.Component{

    constructor(props) {
        super(props);
        this.state = {
            pageSize: 50,
            data:[],
            pageOptions: {
                layout: ['list', 'sep', 'first', 'prev', 'next', 'last', 'sep', 'refresh', 'sep', 'manual', 'info']
            },
            total: 0,
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
            search_rules: {
                start_date: "",
                end_date: ""
            },
            errors: "",
            rules: {
                'start_date': 'required',
                'end_date': ['required']
            },
            formSearchDialogStateClosed: true,

            /*Below are the RecordTx dialog details*/
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
            formRecordTxDialogStateClosed: true,
            tx_types: common.tx_types,
            balance_type: common.balance_type,
            available_balances: "",
            tx_details_row:{}, 
            detailedRecordTxDialogStateClosed:true,
        };
    }

    componentDidMount() {
       
    }

    shouldComponentUpdate(nextProps, nextState) {
        console.log(nextProps);
        /*if (!this.props.statementDialogStateOpened) {
            this.getData();
        }*/
        /*
        if (this.props.statementDialogStateOpened !=  nextProps.statementDialogStateOpened) {
            if (!nextProps.statementDialogStateOpened) {
                this.getData();
            }
        }*/
        return true;
    }

    getData() {
        this.props.loader("START");
        let searchData = {
            search_rules: {
                start_date: common.formatDate(this.state.search_rules.start_date),
                end_date: common.formatDate(this.state.search_rules.end_date)
            },
            merchant_id: this.props.openMerchantAccount.id? this.props.openMerchantAccount.id : null,
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        }
        let url = "";
        if (this.props.openMerchantAccount.id) {
            url = common.base_url+"/transactions/getMerchantStatement";
        } else {
            url = common.base_url+"/transactions/getMerchantStatementByMerchant";
        }

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
                            available_balances: res.balances
                        });
                    } catch (ex) {
                        this.props.messager.alert({
                            title: "Error",
                            icon: "error",
                            msg: ex.message
                        });
                    }
                } else {
                    //If session timed out
                    if (res.code == "107") {
                        this.props.sessionExpired();
                        return;
                    } else if (res.code == "110") {
                        this.props.accessNotAllowed(res.message);
                        return;
                    }

                    this.props.messager.alert({
                        title: "Error "+res.code,
                        icon: "error",
                        msg: res.message
                    });
                }
            } catch(Error) {
                //alert(Error.message);
                this.props.messager.alert({
                    title: "Error",
                    icon: "error",
                    msg: Error.message
                });
                return;
            }
        }).catch((error) => {
            this.props.loader("STOP");
            this.props.messager.alert({
                title: "Error",
                icon: "error",
                msg: error.message
            });
        });
    }

    recordTransactionRequest() {
        this.props.loader("START");
        let data = this.state.record_tx_data;
        data.merchant_id = this.props.openMerchantAccount.id;

        fetch(common.base_url+"/transactions/recordTransaction", {
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
                            this.props.messager.alert({
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
                        this.props.messager.alert({
                            title: "Error",
                            icon: "error",
                            msg: ex.message
                        });
                    }
                } else {
                    //If session timed out
                    if (res.code == "107") {
                        this.props.sessionExpired();
                        return;
                    } else if (res.code == "110") {
                        this.props.accessNotAllowed(res.message);
                        return;
                    }

                    this.props.messager.alert({
                        title: "Error "+res.code,
                        icon: "error",
                        msg: res.message
                    });
                }
            } catch(Error) {
                //alert(Error.message);
                this.props.messager.alert({
                    title: "Error",
                    icon: "error",
                    msg: Error.message
                });
                return;
            }
        }).catch((error) => {
            this.props.loader("STOP");
            this.props.messager.alert({
                title: "Error",
                icon: "error",
                msg: error.message
            });
        });
    }

    addFormAdminRow() {
        
    }

    removeFormAdminRow() {
        
    }

    saveRow() {
        
    }

    resetForm(whenDone) {
        
    }

    handleFormChange(name, value) {
        
    }

    handleSearch() {
        
    }

    download() {

    }

    recordTxSubmit() {
        this.recordTxForm.validate(errors => {
            if (errors != null) {
                return;
            }
            
            this.recordTransactionRequest();
        });
    }

    handleSearchFormChange(name, value) {
        let formd = Object.assign({}, this.state.search_rules);
        formd[name] = value;
        this.setState({ search_rules: formd });
    }

    handleRecordFormChange(name, value) {
        let formd = Object.assign({}, this.state.record_tx_data);
        formd[name] = value;
        this.setState({ record_tx_data: formd });
    }

    clearSearch_() {
        let search_rules = Object.assign({}, this.state.search_rules);
        search_rules.start_date = null;
        search_rules.end_date = null;
        this.setState({ search_rules: search_rules });
    }

    dialog() {
        const { search_rules, rules, formSearchDialogStateClosed } = this.state;
        return (
            <Dialog
                title="Search"
                closed={formSearchDialogStateClosed} 
                style={{ width: 400, height: 210 }}
                bodyCls="f-column"
                modal
                ref={ref => this.dlg = ref}>
                
                <div className="f-full" style={{margin: "5px"}}>
                    <Form
                        ref={ref => this.form = ref}
                        model={search_rules}
                        rules={rules}
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
                                value={search_rules.start_date} 
                                style={styles.formDialogFields}></DateBox>
                        </FormField>
                        <FormField name="end_date" label="End Date">
                            <DateBox 
                                format="yyyy-MM-dd"
                                inputId="end_date" 
                                name="end_date" 
                                value={search_rules.end_date} 
                                style={styles.formDialogFields}></DateBox>
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
                        this.dlg.close();
                    }}>{strings.close}</LinkButton>
                </div>
            </Dialog>
        );
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

    recordTxDialog() {
        const { record_tx_data, record_tx_rules, formRecordTxDialogStateClosed, errors } = this.state;
        return (
            <Dialog
                title={strings.record_tx}
                closed={formRecordTxDialogStateClosed} 
                style={{ width: 500, height: 410 }}
                bodyCls="f-column"
                modal
                onClose={() => {
                    this.resetRecordTxForm(()=>{});
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
                        <FormField name="tx_type" label="Transaction Type:" >
                            <ComboBox
                                inputId="tx_type"
                                name="tx_type"
                                data={this.state.tx_types}
                                value={this.state.record_tx_data.tx_type}
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
                        <FormField name="description" label="Description">
                            <TextBox
                                multiline
                                inputId="description" 
                                name="description" 
                                value={record_tx_data.description} 
                                style={styles.formDialogFieldsTexField}></TextBox>
                        </FormField>
                    </Form>
                </div>
                <div className="dialog-button">
                    <LinkButton style={{ width: 80 }} onClick={() => {
                        this.recordTxSubmit();
                    }}>{strings.save}</LinkButton>
                    <LinkButton style={{ width: 80 }} onClick={() => {
                        this.dlgRecordTx.close();
                    }}>{strings.close}</LinkButton>
                </div>
            </Dialog>
        );
    }


    recordTxDetailsDialog() {
        const { tx_details_row, detailedRecordTxDialogStateClosed } = this.state;
        return (
            <Dialog
                title={"Transaction Details: "+tx_details_row.merchant_name}
                closed={detailedRecordTxDialogStateClosed} 
                style={{ width: 500, height: 410 }}
                bodyCls="f-column"
                modal
                onClose={() => {
                    //this.resetRecordTxForm(()=>{});
                }}
                ref={ref => this.dlgRowDetailsRecordTx = ref}>
                <div className="f-full" style={{margin: "5px"}}>
                    <table with="99%">
                        <tr>
                            <td>Merchant Name</td><td>{tx_details_row.merchant_name}</td>
                            <td>Merchant number</td><td>{tx_details_row.merchant_number}</td>
                            <td>Gateway ID=</td><td>{tx_details_row.gateway_id}</td>
                            <td>Status</td><td>{tx_details_row.status}</td>
                            <td>Amount</td><td>{tx_details_row.original_amount}</td>
                            <td>Merchant Reference:</td><td>{tx_details_row.tx_merchant_ref}</td>
                            <td>Network Ref:</td><td>{tx_details_row.tx_gateway_ref}</td>
                            <td>Payar Number:</td><td>{tx_details_row.payer_number}</td>
                            <td>Created On</td><td>{tx_details_row.created_on}</td>
                            <td>Request Trace</td><td>{tx_details_row.tx_request_trace}</td>
                            <td>Updated Trace</td><td>{tx_details_row.tx_update_trace}</td>
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

    render() {
        const { title, statementDialogStateOpened } = this.props;
        
        return (
            <div>
                <Dialog modal 
                    ref={ref => this.generalDialog = ref}
                    title={title} 
                    closed={statementDialogStateOpened} 
                    style={styles.moreTableContentDialogLargeWidth}
                    borderType="none"
                    onOpen={() => {
                        this.getData();
                    }}
                    onClose={() => {
                        this.props.openOrCloseStatementDialog(true)
                        }}>
                        <Layout style={{ width: 900, border: '0px #FFFFFF' }}>  
                            <div style={{ padding: '5px'}}>
                                <div style={{margin: '5px', float:'left'}}> 
                                    <span style={styles.titleText}>Available Balances: </span>
                                    <span style={styles.numberPresentationGreenBold}>
                                        {this.state.available_balances}
                                    </span>
                                </div>
                                <div  style={{margin: '5px', float:'right'}}>
                                    <ButtonGroup>
                                        <LinkButton 
                                            iconCls="icon-search" 
                                            onClick={() => {
                                                this.dlg.open();
                                                /*this.setState({
                                                    formSearchDialogStateClosed: false
                                                });*/
                                            }}>{strings.search}</LinkButton>
                                        <Download data={this.state.data} />
                                        
                                    </ButtonGroup>
                                </div>
                            </div>
                            <DataGrid
                                ref={ref => this.datagrid = ref}
                                style={{ height: 430, width:899 }}
                                data={this.state.data}
                                pagination
                                {...this.state}>
                                <GridColumn
                                    align="center" 
                                    width="30px"
                                    cellCss="datagrid-td-rownumber"
                                    render={({rowIndex}) => (
                                        <span>{rowIndex+1}</span>
                                    )}
                                    />
                                <GridColumn field="created_on" 
                                    width="150px"
                                    title="Created On" 
                                    ></GridColumn>
                                <GridColumn field="description" 
                                    title="Description" 
                                    render={({ row }) => (
                                        <span>{row.narrative+": "+row.description}</span>
                                    )}
                                    width="250px"
                                    ></GridColumn>
                                <GridColumn field="amount" 
                                    width="120px"
                                    render={({ row }) => (
                                        <span 
                                            style={row.tx_type == "CR" ? styles.numberPresentationGreen : styles.numberPresentationRed}>
                                            {common.formatNumber(row.amount)}
                                        </span>
                                    )}
                                    align="right"
                                    title="Amount" ></GridColumn>
                                <GridColumn field="balances"
                                    align="right"
                                    title="Balance">
                                </GridColumn>
                            </DataGrid>
                            <LayoutPanel region="south" style={{ height: 48 }}>
                                <div className="dialog-button">
                                    <LinkButton className="submit-button-red" 
                                        iconCls="icon-money" style={{ width: 150 }} 
                                            onClick={() => {
                                                this.dlgRecordTx.open();
                                            }}>{strings.record_tx}</LinkButton>
                                    <LinkButton iconCls="icon-cancel" style={{ width: 80 }} 
                                        onClick={() => {
                                            this.generalDialog.close()
                                            //this.props.openOrCloseStatementDialog(true);
                                        }}>Close</LinkButton>
                                </div>
                            </LayoutPanel>
                        </Layout>
                </Dialog>
                {this.dialog()}
                {this.recordTxDialog()}
            </div>
        );
    }
}


class Download extends React.Component {
    render() {
        return (
            <ExcelFile 
                filename="Account_Statement"
                ref={ref => this.excelRef = ref}
                element={<LinkButton 
                    onClick={() => {
                        this.excelRef.download();
                    }}
                    iconCls="icon-excel">{strings.download}</LinkButton>}>
                <ExcelSheet data={this.props.data} name="Statement">
                    <ExcelColumn label="Date time" value="created_on"/>
                    <ExcelColumn label="Description" value="description"/>
                    <ExcelColumn label="Amount" value="amount"/>
                    <ExcelColumn label="Balances"
                                 value={"balances"}/>
                </ExcelSheet>
            </ExcelFile>
        );
    }
}

export default ModuleMerchantAccouunt;
