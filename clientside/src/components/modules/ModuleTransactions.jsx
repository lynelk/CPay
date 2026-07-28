import React from 'react';
import Messager from '../StableMessager';
import { withRouter } from '../../shared/router/compat';
import common from "../Common";
import strings from '../locale';
import {
  Card, Toolbar, Table, Select, SearchField, Checkbox, Badge, Sheet, Button, TextField,
} from '../../ui';

import { apiFetch } from '../../shared/api/httpClient';

const SEARCH_CATEGORIES = [
  { value: 'all', label: 'All Fields' },
  { value: 'tx_type', label: 'Type' },
  { value: 'status', label: 'Status' },
  { value: 'original_amount', label: 'Amount' },
  { value: 'merchant_id', label: 'Merchant ID' },
];

const RESOLVE_STATUS = [
  { value: 'SUCCESSFUL', label: 'SUCCESSFUL' },
  { value: 'FAILED', label: 'FAILED' },
];

function statusTone(s) {
  if (s === 'SUCCESSFUL') return 'success';
  if (s === 'FAILED') return 'danger';
  if (s === 'PENDING') return 'warning';
  return 'neutral';
}

const traceHtml = (value, pre) => ({
  __html: pre
    ? "<pre>" + common.encodeHTML(value || "") + "</pre>"
    : (value ? common.encodeHTML(value).replace(/\n/g, "<br/>") : ""),
});

class ModuleTransactionsC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            total: 0,
            pageSize: 50,
            allChecked: false,
            data: [],
            searchingValue: { value: "", category: "all" },
            hasAccess: false,
            tx_details_row: {},
            detailsOpen: false,
            resolveOpen: false,
            row_resolve_form: { tx_gateway_ref: "", resolve_status: "", id: "" },
        };
    }

    componentDidMount() {
        if (this.isUserAllowedAccess()) {
            this.setState({ hasAccess: true }, () => this.getData());
        } else {
            this.messager.alert({ title: "Access denied!", icon: "info", msg: "You are not allowed access to this section." });
        }
    }

    isUserAllowedAccess() {
        const user = localStorage.getItem("user") != null ? JSON.parse(localStorage.getItem("user")) : {};
        if (user.privileges) {
            for (let i = 0; i < user.privileges.length; i++) {
                if (user.privileges[i].privilege === "ACCESS_TRANSACTION_LOG") return true;
            }
        }
        return false;
    }

    getData() {
        this.props.loader("START");
        const searchData = { pageSize: this.state.pageSize, searchingValue: this.state.searchingValue, sort: 'asc' };
        apiFetch(common.base_url + "/transactions/getTransactions", {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify(searchData)
        }).then((response) => response.text()).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.setState({ data: res.data, total: res.data.length, allChecked: false });
                } else {
                    if (res.code === "107") { this.sessionExpired(); return; }
                    if (res.code === "110") { this.accessNotAllowed(res.message); return; }
                    this.messager.alert({ title: "Error " + res.code, icon: "error", msg: res.message });
                }
            } catch (Error) {
                this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
            }
        }).catch((error) => {
            this.props.loader("STOP");
            if (this.messager != null) this.messager.alert({ title: "Error", icon: "error", msg: error.message });
            else alert(error);
        });
    }

    accessNotAllowed(msg) {
        this.messager.alert({ title: "Access denied!", icon: "info", msg, result: () => this.setState({ hasAccess: false }) });
    }

    sessionExpired() {
        const { history } = this.props;
        this.messager.alert({ title: "Session Expired!", icon: "info", msg: "Your session expired", result: () => history.push("/") });
    }

    resolveTransaction(row) {
        this.messager.confirm({
            title: "Resolve Transaction", icon: "info",
            msg: "Are you sure you want to resolve this transaction to " + row.resolve_status + "?",
            result: (r) => {
                if (!r) return;
                this.setState({ resolveOpen: false });
                this.props.loader("START");
                apiFetch(common.base_url + "/transactions/resolveTransaction", {
                    method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
                    body: JSON.stringify(row)
                }).then((response) => response.text()).then((response_) => {
                    this.props.loader("STOP");
                    let res;
                    try {
                        res = JSON.parse(response_);
                        if (res.code === "000") {
                            this.messager.alert({
                                title: "Success!", icon: "info", msg: res.message,
                                result: (ok) => { if (ok) this.getData(); }
                            });
                        } else {
                            if (res.code === "107") { this.sessionExpired(); return; }
                            this.messager.alert({ title: "Error " + (res.code ? res.code : res.status + " " + res.error), icon: "error", msg: res.message });
                        }
                    } catch (Error) {
                        this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
                    }
                }).catch((error) => {
                    this.props.loader("STOP");
                    this.messager.alert({ title: "Error", icon: "error", msg: error.message });
                });
            }
        });
    }

    handleSearch(value) {
        this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, value } }), () => this.getData());
    }

    handleFormChangeResolve(name, value) {
        this.setState((prev) => ({ row_resolve_form: { ...prev.row_resolve_form, [name]: value } }));
    }

    handleRowCheck(row, checked) {
        const data = this.state.data.map((r) => (r === row ? { ...r, selected: checked } : r));
        this.setState({ data, allChecked: data.every((r) => r.selected) });
    }

    handleAllCheck(checked) {
        this.setState({ allChecked: checked, data: this.state.data.map((r) => ({ ...r, selected: checked })) });
    }

    openDetails(row) {
        this.setState({ tx_details_row: row, detailsOpen: true });
    }

    openResolve() {
        this.setState((prev) => ({
            resolveOpen: true,
            detailsOpen: false,
            row_resolve_form: { ...prev.row_resolve_form, id: prev.tx_details_row.id },
        }));
    }

    renderResolveDialog() {
        const f = this.state.row_resolve_form;
        return (
            <Sheet
                open={this.state.resolveOpen}
                onClose={() => this.setState({ resolveOpen: false })}
                title="Resolve Transaction"
                size="sm"
                footer={<>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ resolveOpen: false })}>Close</Button>
                    <Button variant="primary" className="ios-btn--sm" onClick={() => this.resolveTransaction(this.state.row_resolve_form)}>Submit</Button>
                </>}
            >
                <div className="ios-form">
                    <TextField id="resolve-ref" label="Network Ref" value={f.tx_gateway_ref || ''} onValueChange={(v) => this.handleFormChangeResolve('tx_gateway_ref', v)} />
                    <Select
                        id="resolve-status" label="Resolve to Status"
                        value={f.resolve_status || ''} placeholder="Select status"
                        options={RESOLVE_STATUS}
                        onValueChange={(v) => this.handleFormChangeResolve('resolve_status', v)}
                    />
                </div>
            </Sheet>
        );
    }

    detailRow(label, value) {
        return (
            <div className="cpay-detail-row">
                <span className="cpay-detail-label">{label}</span>
                <span className="cpay-detail-value">{value}</span>
            </div>
        );
    }

    traceBlock(value, pre) {
        return (
            <div
                className="cpay-trace-block"
                dangerouslySetInnerHTML={traceHtml(value, pre)}
            />
        );
    }

    recordTxDetailsDialog() {
        const r = this.state.tx_details_row;
        const canResolve = r.status !== "SUCCESSFUL" && r.status !== "FAILED";
        return (
            <Sheet
                open={this.state.detailsOpen}
                onClose={() => this.setState({ detailsOpen: false })}
                title={"Transaction Details: " + (r.merchant_name || '')}
                size="lg"
                footer={<>
                    {canResolve ? <Button variant="primary" className="ios-btn--sm" onClick={() => this.openResolve()}>{strings.resolve}</Button> : null}
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ detailsOpen: false })}>{strings.close}</Button>
                </>}
            >
                {this.detailRow('Merchant Name', r.merchant_name)}
                {this.detailRow('Merchant number', r.merchant_number)}
                {this.detailRow('Gateway ID', r.gateway_id)}
                {this.detailRow('Status', <Badge tone={statusTone(r.status)}>{r.status}</Badge>)}
                {this.detailRow('Amount', "UGX " + (r.original_amount_formatted || ''))}
                {this.detailRow('Merchant Reference', r.tx_merchant_ref)}
                {this.detailRow('Network Ref', r.tx_gateway_ref)}
                {this.detailRow('Payer/Payee Number', r.payer_number)}
                {this.detailRow('Merchant Description', this.traceBlock(r.tx_merchant_description, false))}
                {this.detailRow('Our Description', this.traceBlock(r.tx_description, false))}
                {this.detailRow('Charges', "UGX " + (r.charges_formatted || ''))}
                {this.detailRow('Created On', r.created_on)}
                {this.detailRow('Request Trace', this.traceBlock(r.tx_request_trace, true))}
                {this.detailRow('Updated Trace', this.traceBlock(r.tx_update_trace, true))}
                {this.detailRow('Callback Trace', this.traceBlock(r.callback_trace, true))}
            </Sheet>
        );
    }

    render() {
        const { searchingValue, data, allChecked } = this.state;
        if (!this.state.hasAccess) {
            return <div><Messager ref={ref => this.messager = ref}></Messager></div>;
        }

        const columns = [
            {
                key: 'ck', width: 44,
                header: <Checkbox checked={allChecked} onCheckedChange={(c) => this.handleAllCheck(c)} />,
                render: (row) => <Checkbox checked={Boolean(row.selected)} onCheckedChange={(c) => this.handleRowCheck(row, c)} />,
            },
            { key: 'created_on', header: 'Created On', accessor: (r) => r.created_on, sortable: true, sortValue: (r) => r.created_on || '' },
            { key: 'merchant_id', header: 'Merchant', render: (r) => r.merchant_name, sortable: true, sortValue: (r) => r.merchant_name || '' },
            { key: 'payer_number', header: 'Payer Number', accessor: (r) => r.payer_number },
            { key: 'tx_merchant_ref', header: 'Merchant Ref', accessor: (r) => r.tx_merchant_ref },
            { key: 'status', header: 'Status', render: (r) => <Badge tone={statusTone(r.status)}>{r.status}</Badge>, sortable: true, sortValue: (r) => r.status || '' },
            { key: 'tx_type', header: 'Type', accessor: (r) => r.tx_type },
            { key: 'original_amount_formatted', header: 'Amount', numeric: true, render: (r) => "UGX " + (r.original_amount_formatted || ''), sortable: true, sortValue: (r) => Number(r.original_amount) || 0 },
            {
                key: 'actions', header: 'Actions', align: 'center',
                render: (row) => <Button variant="ghost" className="ios-btn--sm" onClick={() => this.openDetails(row)}>Details</Button>,
            },
        ];

        return (
            <Card flush>
                <div style={{ padding: 'var(--ios-space-4)' }}>
                    <Toolbar>
                        <div style={{ minWidth: 160 }}>
                            <Select id="tx-category" value={searchingValue.category} options={SEARCH_CATEGORIES} onValueChange={(v) => this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, category: v } }))} />
                        </div>
                        <Toolbar.Spacer />
                        <SearchField
                            value={searchingValue.value}
                            onValueChange={(v) => this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, value: v } }))}
                            onSubmit={(v) => this.handleSearch(v)}
                            placeholder={strings.search_merchant}
                        />
                    </Toolbar>
                </div>
                <Table
                    columns={columns}
                    rows={data}
                    rowKey={(row, i) => row.id ?? `${row.tx_merchant_ref}-${i}`}
                    pageSize={this.state.pageSize}
                    isRowSelected={(row) => Boolean(row.selected)}
                    emptyText="No transactions to display."
                />
                {this.recordTxDetailsDialog()}
                {this.renderResolveDialog()}
                <Messager ref={ref => this.messager = ref}></Messager>
            </Card>
        );
    }
}

const ModuleTransactions = withRouter(ModuleTransactionsC);

export default ModuleTransactions;
