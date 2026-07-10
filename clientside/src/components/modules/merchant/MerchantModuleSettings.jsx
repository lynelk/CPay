import React from 'react';
import Messager from '../../StableMessager';
import { withRouter } from '../../../shared/router/compat';
import common from "../../Common";
import { isSensitiveSetting, maskedSettingValue } from '../settingsGridHelpers';
import { Card, Toolbar, Table, Button, PasswordField, TextArea, Icons } from '../../../ui';

class MerchantModuleSettingsC extends React.Component {
    constructor(props) {
        super(props);
        this.state = { data: [] };
    }

    componentDidMount() {
        this.getData();
    }

    getData() {
        this.props.loader("START");
        fetch(common.base_url + "/settings/getMerchantSettings", {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify({ settings: "all", merchant_id: this.props.merchant_id })
        }).then((response) => response.text()).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.setState({ data: Array.isArray(res.data) ? res.data : [] });
                } else {
                    if (res.code === "107") { this.props.sessionExpired(); return; }
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
        fetch(common.base_url + "/settings/updateMerchantSettings", {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify(this.state.data)
        }).then((response) => response.text()).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.messager.alert({ title: "Success!", icon: "info", msg: res.message });
                } else {
                    if (res.code === "107") { this.props.sessionExpired(); return; }
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

    updateValue(row, value) {
        this.setState((prev) => ({
            data: prev.data.map((r) => (r === row ? { ...r, setting_value: value } : r)),
        }));
    }

    renderEditor(row) {
        if (isSensitiveSetting(row)) {
            return (
                <PasswordField
                    id={`mset-${row.setting_key || row.label}`}
                    label=""
                    value={row.setting_value || ''}
                    onValueChange={(v) => this.updateValue(row, v)}
                    placeholder={maskedSettingValue}
                />
            );
        }
        return (
            <TextArea
                id={`mset-${row.setting_key || row.label}`}
                label=""
                rows={2}
                value={row.setting_value || ''}
                onValueChange={(v) => this.updateValue(row, v)}
            />
        );
    }

    render() {
        const columns = [
            { key: 'label', header: 'Settings Label', accessor: (r) => r.label, width: '38%' },
            { key: 'setting_value', header: 'Setting Value', render: (r) => this.renderEditor(r) },
        ];

        return (
            <Card flush>
                <div style={{ padding: 'var(--ios-space-4)' }}>
                    <Toolbar>
                        <Button variant="primary" className="ios-btn--sm" onClick={() => this.saveSettings()}>
                            <Icons.SettingsIcon size={16} />Save Settings
                        </Button>
                    </Toolbar>
                </div>
                <Table
                    columns={columns}
                    rows={this.state.data}
                    rowKey={(row, i) => row.setting_key ?? `${row.label}-${i}`}
                    groupBy={(row) => row.setting_group || 'General'}
                    renderGroupHeader={(group) => group}
                    emptyText="No settings to display."
                />
                <Messager ref={ref => this.messager = ref}></Messager>
            </Card>
        );
    }
}

const MerchantModuleSettings = withRouter(MerchantModuleSettingsC);

export default MerchantModuleSettings;
