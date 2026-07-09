import React from 'react';
import { DataGrid, GridColumn, LinkButton, Panel, PasswordBox, TextBox } from 'rc-easyui';
import Messager from '../StableMessager';
import { withRouter } from '../../shared/router/compat';
import common from "../Common";
import styles from '../styles';
import { isSensitiveSetting, maskedSettingValue } from './settingsGridHelpers';

class ModuleSettingsC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            data: [],
            windowHeight: window.innerHeight
        };
        this.handleResize = this.handleResize.bind(this);
    }

    handleResize() {
        this.setState({ windowHeight: window.innerHeight });
    }

    componentDidMount() {
        window.addEventListener("resize", this.handleResize);
        this.getData();
    }

    componentWillUnmount() {
        window.removeEventListener("resize", this.handleResize);
    }

    renderGroup({ value }) {
        return <span style={{ fontWeight: 'bold' }}>{value}</span>;
    }

    getData() {
        this.props.loader("START");
        fetch(common.base_url + "/settings/getSettings", {
            method: 'POST',
            mode: 'cors',
            cache: 'no-cache',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            redirect: 'follow',
            referrer: 'no-referrer',
            body: JSON.stringify({ settings: "all" })
        }).then((response) => response.text())
            .then((response_) => {
                this.props.loader("STOP");
                let res;
                try {
                    res = JSON.parse(response_);
                    if (res.code === "000") {
                        this.setState({ data: Array.isArray(res.data) ? res.data : [] });
                    } else {
                        if (res.code === "107") {
                            this.props.sessionExpired();
                            return;
                        }
                        this.messager.alert({ title: "Error " + res.code, icon: "error", msg: res.message });
                    }
                } catch (Error) {
                    this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
                }
            }).catch((error) => {
                this.props.loader("STOP");
                this.messager.alert({ title: "Error", icon: "error", msg: error.message });
            });
    }

    saveSettings() {
        this.props.loader("START");
        fetch(common.base_url + "/settings/updateSettings", {
            method: 'POST',
            mode: 'cors',
            cache: 'no-cache',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            redirect: 'follow',
            referrer: 'no-referrer',
            body: JSON.stringify(this.state.data)
        }).then((response) => response.text())
            .then((response_) => {
                this.props.loader("STOP");
                let res;
                try {
                    res = JSON.parse(response_);
                    if (res.code === "000") {
                        this.messager.alert({ title: "Success!", icon: "info", msg: res.message });
                    } else {
                        if (res.code === "107") {
                            this.props.sessionExpired();
                            return;
                        }
                        this.messager.alert({ title: "Error " + res.code, icon: "error", msg: res.message });
                    }
                } catch (Error) {
                    this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
                }
            }).catch((error) => {
                this.props.loader("STOP");
                this.messager.alert({ title: "Error", icon: "error", msg: error.message });
            });
    }

    renderSettingValueEditor(row) {
        if (isSensitiveSetting(row)) {
            return (
                <PasswordBox
                    value={row.setting_value || ''}
                    placeholder={maskedSettingValue}
                    iconCls="icon-lock"
                    style={{ width: '100%' }} />
            );
        }

        return (
            <TextBox
                multiline
                value={row.setting_value || ''}
                style={{ width: '100%', height: 120 }} />
        );
    }

    render() {
        const { windowHeight } = this.state;
        return (
            <div>
                <Panel bodyStyle={{ padding: '5px' }}>
                    <div style={{ float: 'left' }}>
                        <LinkButton
                            onClick={() => this.saveSettings()}
                            className={styles.moduleToolBarButtons}
                            iconCls="icon-settings">Save Settings</LinkButton>
                    </div>
                </Panel>
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
                    <GridColumn
                        field="setting_value"
                        editable
                        editor={({ row }) => this.renderSettingValueEditor(row)}
                        title="Setting Value"></GridColumn>
                </DataGrid>
                <Messager ref={ref => this.messager = ref}></Messager>
            </div>
        );
    }
}

const ModuleSettings = withRouter(ModuleSettingsC);

export default ModuleSettings;