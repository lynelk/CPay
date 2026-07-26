import React from 'react';
import Messager from '../../StableMessager';
import { withRouter } from '../../../shared/router/compat';
import common from "../../Common";
import strings from '../../locale';
import ReactExport from "../../../shared/export/ExcelExport";
import {
  Card, Toolbar, Table, Select, SearchField, Checkbox, Badge, Sheet, Button,
  TextField, TextArea, DateField, Icons,
} from '../../../ui';

import { apiFetch } from '../../../shared/api/httpClient';

const ExcelFile = ReactExport.ExcelFile;
const ExcelSheet = ReactExport.ExcelFile.ExcelSheet;
const ExcelColumn = ReactExport.ExcelFile.ExcelColumn;

const SEARCH_CATEGORIES = [
  { value: 'all', label: 'All Fields' },
  { value: 'tx_type', label: 'Type' },
  { value: 'status', label: 'Status' },
  { value: 'original_amount', label: 'Amount' },
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

class MerchantModuleTransactionsC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            total: 0,
            pageSize: 50,
            allChecked: false,
            data: [],
            searchingValue: { value: "", category: "all" },
            search_rules: {
                start_date: common.formatDate(common.getDateMonthsBefore(new Date(), 6)),
                end_date: common.formatDate(new Date()),
                status: "",
                tx_type: "",
            },
            searchOpen: false,
            hasAccess: false,
            tx_details_row: {},
            detailsOpen: false,
            payInOpen: false,
            payInForm: { account: "", tx_description: "", amount: "0" },
            payInErrors: {},
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
        const user = localStorage.getItem("merchantUser") != null ? JSON.parse(localStorage.getItem("merchantUser")) : {};
        if (user.privileges) {
            for (let i = 0; i < user.privileges.length; i++) {
                if (user.privileges[i].privilege === "ACCESS_TRANSACTION_LOG") return true;
            }
        }
        return false;
    }

    getData() {
        this.props.loader("START");
        const searchData = {
            search_rules: this.state.search_rules,
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        };
        apiFetch(common.base_url + "/transactions/getMerchantTransactions", {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify(searchData)
        }).then((response) => response.text()).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.setState({ data: res.data, total: res.total, allChecked: false });
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

    submitPayment(data) {
        this.messager.confirm({
            title: "Confirm to Initiate inbound Payment", icon: "info",
            msg: "Are you sure you want to continue to initiate a mobile money payment on " + data.account + "?",
            result: (r) => {
                if (!r) return;
                this.props.loader("START");
                apiFetch(common.base_url + "/transactions/addPayInTransaction", {
                    method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
                    body: JSON.stringify(data)
                }).then((response) => response.text()).then((response_) => {
                    this.props.loader("STOP");
                    let res;
                    try {
                        res = JSON.parse(response_);
                        if (res.code === "000") {
                            this.setState({ payInOpen: false, payInForm: { account: "", tx_description: "", amount: "0" } }, () => {
                                this.messager.alert({
                                    title: "Success!", icon: "info", msg: res.message,
                                    result: (ok) => { if (ok) this.getData(); }
                                });
                            });
                        } else {
                            if (res.code === "107") { this.sessionExpired(); return; }
                            this.messager.alert({ title: "Error " + (res.code ? res.code : res.status + " " + res.error), icon: "error", msg: res.message + ". Error: " + res.error });
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

    handleSearchFormChange(name, value) {
        this.setState((prev) => ({ search_rules: { ...prev.search_rules, [name]: value } }));
    }

    clearSearch() {
        this.setState({
            search_rules: {
                start_date: common.formatDate(common.getDateMonthsBefore(new Date(), 6)),
                end_date: common.formatDate(new Date()),
                status: "",
                tx_type: "",
            }
        });
    }

    handleRowCheck(row, checked) {
        const data = this.state.data.map((r) => (r === row ? { ...r, selected: checked } : r));
        this.setState({ data, allChecked: data.every((r) => r.selected) });
    }

    handleAllCheck(checked) {
        this.setState({ allChecked: checked, data: this.state.data.map((r) => ({ ...r, selected: checked })) });
    }

    openPayIn() {
        this.setState({ payInOpen: true, payInErrors: {}, payInForm: { account: "", tx_description: "", amount: "0" } });
    }

    payInChange(name, value) {
        this.setState((prev) => ({ payInForm: { ...prev.payInForm, [name]: value } }));
    }

    savePayIn() {
        const f = this.state.payInForm;
        const errors = {};
        if (!f.account) errors.account = 'Account is required';
        if (!f.tx_description) errors.tx_description = 'Description is required';
        if (f.amount === '' || Number.isNaN(Number(f.amount))) errors.amount = 'Enter a valid amount';
        this.setState({ payInErrors: errors });
        if (Object.keys(errors).length === 0) this.submitPayment(f);
    }

    searchDialog() {
        const s = this.state.search_rules;
        return (
            <Sheet
                open={this.state.searchOpen}
                onClose={() => this.setState({ searchOpen: false })}
                title="Search"
                size="sm"
                footer={<>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.clearSearch()}>{strings.clear}</Button>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ searchOpen: false })}>{strings.close}</Button>
                    <Button variant="primary" className="ios-btn--sm" onClick={() => this.setState({ searchOpen: false }, () => this.getData())}>{strings.go}</Button>
                </>}
            >
                <div className="ios-form">
                    <DateField id="tx-start" label="Start Date" kind="date" value={s.start_date} onValueChange={(v) => this.handleSearchFormChange('start_date', v)} />
                    <DateField id="tx-end" label="End Date" kind="date" value={s.end_date} onValueChange={(v) => this.handleSearchFormChange('end_date', v)} />
                    <TextField id="tx-status" label="Status" value={s.status} onValueChange={(v) => this.handleSearchFormChange('status', v)} />
                    <TextField id="tx-type" label="Type" value={s.tx_type} onValueChange={(v) => this.handleSearchFormChange('tx_type', v)} />
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
        return (
            <Sheet
                open={this.state.detailsOpen}
                onClose={() => this.setState({ detailsOpen: false })}
                title={"Transaction Details: " + (r.merchant_name || '')}
                size="lg"
                footer={<Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ detailsOpen: false })}>{strings.close}</Button>}
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
                {this.detailRow('Callback Trace', this.traceBlock(r.callback_trace, true))}
            </Sheet>
        );
    }

    payInDialog() {
        const { payInForm: f, payInErrors: e } = this.state;
        return (
            <Sheet
                open={this.state.payInOpen}
                onClose={() => this.setState({ payInOpen: false })}
                title={strings.add_payin}
                size="sm"
                footer={<>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ payInOpen: false })}>Close</Button>
                    <Button variant="primary" className="ios-btn--sm" onClick={() => this.savePayIn()}>{strings.submit}</Button>
                </>}
            >
                <div className="ios-form">
                    <TextField id="payin-account" label="Account (e.g 256772123456)" value={f.account} invalid={Boolean(e.account)} onValueChange={(v) => this.payInChange('account', v)} />
                    {e.account ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{e.account}</span> : null}
                    <TextField id="payin-amount" label="Amount" value={f.amount} invalid={Boolean(e.amount)} onValueChange={(v) => this.payInChange('amount', v)} />
                    {e.amount ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{e.amount}</span> : null}
                    <TextArea id="payin-desc" label="Description" rows={3} value={f.tx_description} invalid={Boolean(e.tx_description)} onValueChange={(v) => this.payInChange('tx_description', v)} />
                    {e.tx_description ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{e.tx_description}</span> : null}
                </div>
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
            { key: 'merchant_id', header: 'Network ID', render: (r) => r.tx_gateway_ref },
            { key: 'payer_number', header: 'Payer Number', accessor: (r) => r.payer_number },
            { key: 'status', header: 'Status', render: (r) => <Badge tone={statusTone(r.status)}>{r.status}</Badge>, sortable: true, sortValue: (r) => r.status || '' },
            { key: 'tx_type', header: 'Type', accessor: (r) => r.tx_type },
            { key: 'original_amount_formatted', header: 'Amount', numeric: true, render: (r) => "UGX " + (r.original_amount_formatted || ''), sortable: true, sortValue: (r) => Number(r.original_amount) || 0 },
            {
                key: 'actions', header: 'Actions', align: 'center',
                render: (row) => <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ tx_details_row: row, detailsOpen: true })}>Details</Button>,
            },
        ];

        return (
            <Card flush>
                <div style={{ padding: 'var(--ios-space-4)' }}>
                    <Toolbar>
                        <Button variant="primary" className="ios-btn--sm" onClick={() => this.openPayIn()}>
                            <Icons.PlusIcon size={16} />{strings.add_payin}
                        </Button>
                        <Toolbar.Spacer />
                        <div style={{ minWidth: 150 }}>
                            <Select id="mtx-category" value={searchingValue.category} options={SEARCH_CATEGORIES} onValueChange={(v) => this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, category: v } }))} />
                        </div>
                        <SearchField
                            value={searchingValue.value}
                            onValueChange={(v) => this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, value: v } }))}
                            onSubmit={(v) => this.handleSearch(v)}
                            placeholder={strings.search_merchant}
                        />
                        <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ searchOpen: true })}>
                            <Icons.SearchIcon size={16} />{strings.search}
                        </Button>
                        <Download data={data} />
                    </Toolbar>
                </div>
                <Table
                    columns={columns}
                    rows={data}
                    rowKey={(row, i) => row.id ?? `${row.tx_gateway_ref}-${i}`}
                    pageSize={this.state.pageSize}
                    isRowSelected={(row) => Boolean(row.selected)}
                    emptyText="No transactions to display."
                />
                {this.searchDialog()}
                {this.recordTxDetailsDialog()}
                {this.payInDialog()}
                <Messager ref={ref => this.messager = ref}></Messager>
            </Card>
        );
    }
}

class Download extends React.Component {
    render() {
        return (
            <ExcelFile
                filename="Account_Transactions"
                ref={ref => this.excelRef = ref}
                element={
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.excelRef.download()}>
                        <Icons.DownloadIcon size={16} />{strings.download}
                    </Button>
                }>
                <ExcelSheet data={this.props.data} name="Transactions">
                    <ExcelColumn label="Date time" value="created_on" />
                    <ExcelColumn label="Network ID" value="tx_gateway_ref" />
                    <ExcelColumn label="Payer Number" value="payer_number" />
                    <ExcelColumn label="Status" value="status" />
                    <ExcelColumn label="Type" value={"tx_type"} />
                    <ExcelColumn label="Merchant Reference" value="tx_merchant_ref" />
                    <ExcelColumn label="Description" value="tx_merchant_description" />
                    <ExcelColumn label="Amount" value="original_amount" />
                    <ExcelColumn label="Charges" value="charges" />
                </ExcelSheet>
            </ExcelFile>
        );
    }
}

const MerchantModuleTransactions = withRouter(MerchantModuleTransactionsC);

export default MerchantModuleTransactions;
