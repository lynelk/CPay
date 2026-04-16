import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox } from 'rc-easyui';
import { Panel, Layout, LayoutPanel, Messager, Menu, MenuItem } from 'rc-easyui';
import { DataGrid, GridColumn, Label, ButtonGroup, SearchBox, Dialog } from 'rc-easyui';
import PropTypes from "prop-types";
import { useHistory, withRouter } from "react-router-dom";
import MainMenu from "../../MainMenu";
import common from "../../Common";
import Progress from "../../Progress";
import LinearChart from './LinearChart';
import styles from '../../styles';
import strings from '../../locale';

class MerchantModuleAdminsC extends React.Component {
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
                email: "",
                name: "",
                phone: "",
                password: "",
                privileges:[],
                status: { value: 'ACTIVE', text: "ACTIVE" },
            },
            privileges: common.merchant_privileges,
            status: [{ value: 'ACTIVE', text: "ACTIVE" },
                { value: 'INACTIVE', text: "INACTIVE" },
                { value: 'SUSPENDED', text: "SUSPENDED" }],
            rules: {
                'name': 'required',
                'status': ['required'],
                "email": {"required":true,"emailValidation":common.emailValidation},
                "phone": {'required':true, "phoneValidation":common.phoneValidation}
            },
            errors: {},
            title: '',
            formDialogState: true,
            searchingValue: {
                value: "",
                category: ""
            },
            categories: [
                {value:'all',text:'All Fields',iconCls:'icon-ok'},
                {value:'email',text:'Email', iconCls:'icon-settings'},
                {value:'status',text:'Status', iconCls:'icon-settings'},
                {value:'phone',text:'Phone', iconCls:'icon-man'},
                {value:'name',text:'Name', iconCls:'icon-man'}
            ],
            hasAccess: false,
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
                email: "",
                name: "",
                phone: "",
                password: "",
                privileges:[],
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
        fetch(common.base_url+"/admins/getAdminsMerchant", {
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
            this.messager.alert({
                title: "Error",
                icon: "error",
                msg: error.message
            });
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

    editRow(row) {
        let formd = Object.assign({}, row);
        formd.password = "";
        this.setState({ 
            formdMode: 'edit',
            formd: formd,
            formDialogState: false,
            title: "Add new Admin ("+row.name+")"
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
                    fetch(common.base_url+"/admins/deleteAdminMerchant", {
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
        this.setState({
            formDialogState: false,
            title: "Add new Admin"
        });
    }

    handleSearch(searchingValue) {
        this.setState({
            searchingValue: searchingValue
        }, () => {
            this.getData();
        });
        //alert(JSON.stringify(searchingValue));
    }

    handleClear() {

    }

    saveRow() {
        //alert(JSON.stringify(this.state.formd));
        this.form.validate(errors => {
            if (errors != null) {
                return;
            }
            let url;
            if (this.state.formdMode == "edit") {
                url = common.base_url+"/admins/editAdminMerchant";
            } else {
                url = common.base_url+"/admins/addAdminMerchant";
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
                body: JSON.stringify(this.state.formd) // body data type must match "Content-Type" header
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
        });
    }

    handleFormChange(name, value) {
        let formd = Object.assign({}, this.state.formd);
        formd[name] = value;
        this.setState({ formd: formd })
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


    renderDialog() {
        const row = this.state.formd;
        const { title, formDialogState, rules } = this.state;
        return (
            <Dialog modal 
                title={title} 
                closed={formDialogState} 
                style={styles.formDialog}
                borderType="none"
                onClose={() => this.setState({ formDialogState: true })}>
                    <Layout style={{ width: 500, height:'100%', border: '0px #FFFFFF' }}>
                        <LayoutPanel 
                            region="north" 
                            split={false}
                            style={{ height: 320, border: '0px #FFFFFF' }}>
                            <div style={styles.formDialogContainer}>
                                <Form
                                    ref={ref => this.form = ref}
                                    model={row}
                                    rules={rules}
                                    floatingLabel
                                    labelWidth={120}
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
                                    <FormField name="phone" label="Phone">
                                        <TextBox 
                                            inputId="phone" 
                                            name="phone" 
                                            value={row.phone} 
                                            style={styles.formDialogFields}></TextBox>
                                    </FormField>
                                    <FormField name="email" label="Email">
                                        <TextBox 
                                            inputId="email" 
                                            name="email" 
                                            value={row.email} 
                                            style={styles.formDialogFields}></TextBox>
                                    </FormField>
                                    <FormField name="password" label="User Password">
                                        <PasswordBox
                                            inputId="password" 
                                            name="password" 
                                            value={row.password} 
                                            iconCls="icon-lock"
                                            style={styles.formDialogFields}></PasswordBox>
                                    </FormField>
                                    <FormField name="status" label="Status:" >
                                        <ComboBox
                                            inputId="status"
                                            name="status"
                                            data={this.state.status}
                                            value={this.state.formd.status}
                                            style={styles.formDialogFields}
                                            onChange={(value) => this.setState({ value: value })}
                                            />
                                    </FormField>
                                    <FormField name="privileges" label="Privileges">
                                        <ComboBox
                                            inputId="privileges"
                                            name="privileges"
                                            multiple
                                            data={this.state.privileges}
                                            value={this.state.formd.privileges}
                                            style={styles.formDialogFields}
                                            onChange={(value) => this.setState({ value: value })}
                                            />
                                    </FormField>
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
                                            this.setState({ formDialogState: true });
                                        });
                                    }}>Close</LinkButton>
                            </div>
                        </LayoutPanel>
                    </Layout>
            </Dialog>
        );
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
        })
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
                            <span class='pg-subtitles'>Admins | </span>
                            <ComboBox
                                inputId="c1"
                                data={this.state.gridActions}
                                value={this.state.gridActionsValue}
                                onChange={(value) => this.bulkActions(value)}/>
                            <LinkButton 
                                onClick={() => this.addNew()}
                                style={styles.moduleToolBarButtons}
                                iconCls="icon-add">{strings.add_admin}</LinkButton>
                        </div>
                        <SearchBox
                            style={{ width: 300, float:'right' }}
                            placeholder={strings.search_admin}
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
                    <GridColumn field="email" title="Email" align="left"></GridColumn>
                    <GridColumn field="phone" title="phone" align="left"></GridColumn>
                    <GridColumn field="status" title="status" align="left"></GridColumn>
                    <GridColumn field="note" title="Actions" align="center"
                        render={({ row }) => (
                            <div>
                                <ButtonGroup>
                                    <LinkButton iconCls="icon-edit" onClick={() => this.editRow(row)}>Edit</LinkButton>
                                    <LinkButton iconCls="icon-remove" onClick={() => this.deleteRow(row)}>Delete</LinkButton>
                                </ButtonGroup>
                            </div>
                        )}>

                    </GridColumn>
                </DataGrid>
                <Messager ref={ref => this.messager = ref}></Messager>
                {this.renderDialog()}
            </div>
        );
    }
}

const MerchantModuleAdmins = withRouter(MerchantModuleAdminsC);

export default MerchantModuleAdmins;