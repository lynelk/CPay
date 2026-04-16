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


class MerchantModuleSettingsC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            data:[],
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
        this.getData();
    }

    renderGroup({value,rows} ) {
        return (
            <span style={{fontWeight:'bold'}}>
                {value}
            </span>
        )
    }

    getData() {
        this.props.loader("START");
        let searchData = { 
            settings: "all",
            merchant_id: this.props.merchant_id
        };
        fetch(common.base_url+"/settings/getMerchantSettings", {
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
                        console.log(res.data);
                        //return;
                        this.setState({
                            data: res.data,
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
                        this.props.sessionExpired();
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

    saveSettings() {
        this.props.loader("START");
        fetch(common.base_url+"/settings/updateMerchantSettings", {
            method: 'POST', // *GET, POST, PUT, DELETE, etc.
            mode: 'cors', // no-cors, *cors, same-origin
            cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
            credentials: 'include', // include, *same-origin, omit
            headers: {
                'Content-Type': 'application/json',
            },
            redirect: 'follow', // manual, *follow, error
            referrer: 'no-referrer', // no-referrer, *client
            body: JSON.stringify(this.state.data) // body data type must match "Content-Type" header
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
                        this.props.sessionExpired();
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

    render() {
        const {windowHeight} = this.state;
        return (
            <div>
                <div>
                    <Panel bodyStyle={{ padding: '5px'}}>
                        <div style={{float:'left'}}>
                            <span class='pg-subtitles'>Settings | </span>
                            <LinkButton 
                                onClick={() => this.saveSettings()}
                                style={styles.moduleToolBarButtons}
                                iconCls="icon-settings">Save Settings</LinkButton>
                        </div>
                    </Panel>
                </div>
                <DataGrid
                    clickToEdit
                    selectionMode="cell"
                    editMode="cell"
                    ref={ref => this.dataGrid = ref}
                    style={{ height: (windowHeight - common.toReduceGridHeight) }}
                    groupField="setting_group"
                    renderGroup={this.renderGroup}
                    data={this.state.data}>
                    <GridColumn field="label" title="Settings Label"></GridColumn>
                    <GridColumn field="setting_value" 
                        editable
                        editor={({row})=>(
                            <TextBox 
                                multiline 
                                value={row.setting_value} 
                                style={{ width: '100%', height: 120 }}>{row.setting_value}</TextBox>
                        )}
                        title="Setting Value"></GridColumn>
                </DataGrid>
                <Messager ref={ref => this.messager = ref}></Messager>
            </div>
        );
    }
}


const MerchantModuleSettings = withRouter(MerchantModuleSettingsC);

export default MerchantModuleSettings;