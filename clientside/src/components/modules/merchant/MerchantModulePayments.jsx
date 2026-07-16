import React from 'react';
import Messager from '../../StableMessager';
import { withRouter } from '../../../shared/router/compat';
import common from "../../Common";
import strings from '../../locale';
import {
  Card, Toolbar, Table, Select, SearchField, Checkbox, Badge, Sheet, Button,
  TextField, TextArea, FileButton, Icons,
} from '../../../ui';

const SEARCH_CATEGORIES = [
  { value: 'all', label: 'All Fields' },
  { value: 'account_number', label: 'Merchant Account' },
  { value: 'status', label: 'Status' },
  { value: 'account_type', label: 'Business Type' },
  { value: 'name', label: 'Name' },
];

const toOptions = (arr) => (Array.isArray(arr) ? arr.map((t) => ({ value: t.value, label: t.text })) : []);

function statusTone(s) {
  if (s === 'DONE') return 'success';
  if (s === 'STOPPED') return 'danger';
  if (s === 'PROCESSING') return 'info';
  if (s === 'PENDING') return 'warning';
  return 'neutral';
}

class MerchantModulePaymentsC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            total: 0,
            pageSize: 50,
            allChecked: false,
            data: [],
            formdMode: 'new',
            formd: { beneficiaries: [], id: "", name: "", tx_description: "" },
            title: '',
            formOpen: false,
            searchingValue: { value: "", category: "all" },
            hasAccess: false,
            tx_details_row: {},
            detailsOpen: false,
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
                const p = user.privileges[i].privilege;
                if (p === "CREATE_BATCH_TX" || p === "APPROVE_BATCH_TX" || p === "DOWNLOAD_REPORTS") return true;
            }
        }
        return false;
    }

    getData() {
        this.props.loader("START");
        const searchData = { pageSize: this.state.pageSize, searchingValue: this.state.searchingValue, sort: 'asc' };
        fetch(common.base_url + "/transactions/getMerchantPayments", {
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
        });
    }

    accessNotAllowed(msg) {
        this.messager.alert({ title: "Access denied!", icon: "info", msg, result: () => this.setState({ hasAccess: false }) });
    }

    sessionExpired() {
        const { history } = this.props;
        this.messager.alert({ title: "Session Expired!", icon: "info", msg: "Your session expired", result: () => history.push("/portal") });
    }

    paymentAction(url, row, verb) {
        this.messager.confirm({
            title: `${verb} this Payment`, icon: "info", msg: `Are you sure you want to ${verb.toLowerCase()} this payment?`,
            result: (r) => {
                if (!r) return;
                this.props.loader("START");
                fetch(common.base_url + url, {
                    method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
                    body: JSON.stringify(row)
                }).then((response) => response.text()).then((response_) => {
                    this.props.loader("STOP");
                    let res;
                    try {
                        res = JSON.parse(response_);
                        if (res.code === "000") {
                            this.messager.alert({ title: "Success!", icon: "info", msg: res.message, result: (ok) => { if (ok) this.getData(); } });
                        } else {
                            if (res.code === "107") { this.sessionExpired(); return; }
                            this.messager.alert({ title: "Error " + (res.code ? res.code : res.status + " " + res.error), icon: "error", msg: res.message + ". Error: " + res.error });
                        }
                    } catch (Error) {
                        this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
                    }
                }).catch((error) => {
                    this.props.loader("STOP");
                    if (this.messager != null) this.messager.alert({ title: "Error", icon: "error", msg: error.message });
                });
            }
        });
    }

    attemptToStart(row) { this.paymentAction("/transactions/startPayment", row, "Start"); }
    attemptToStop(row) { this.paymentAction("/transactions/stopPayment", row, "Stop"); }

    addNew() {
        this.setState({ title: strings.add_payment, formdMode: 'new', formOpen: true, formd: { beneficiaries: [], id: "", name: "", tx_description: "" } });
    }

    editRow(row) {
        this.setState({
            formdMode: 'edit',
            formd: { ...row, generate_password: false, generate_new_keys: false, beneficiaries: row.beneficiaries || [] },
            formOpen: true,
            title: "Edit Payment (" + row.name + ")",
        });
    }

    handleSearch(value) {
        this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, value } }), () => this.getData());
    }

    saveRow(data) {
        const url = this.state.formdMode === "edit"
            ? common.base_url + "/transactions/editPayment"
            : common.base_url + "/transactions/addPayment";
        this.props.loader("START");
        fetch(url, {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify(data)
        }).then((response) => response.text()).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.messager.alert({
                        title: "Success!", icon: "info", msg: res.message,
                        result: (ok) => { if (ok) this.setState({ formOpen: false }, () => this.getData()); }
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

    handleRowCheck(row, checked) {
        const data = this.state.data.map((r) => (r === row ? { ...r, selected: checked } : r));
        this.setState({ data, allChecked: data.every((r) => r.selected) });
    }

    handleAllCheck(checked) {
        this.setState({ allChecked: checked, data: this.state.data.map((r) => ({ ...r, selected: checked })) });
    }

    returnPaymentAction(row) {
        if (row.status === "DONE" || row.status === "STOPPED") return null;
        const startLabel = row.status === "PROCESSING" ? "Pause" : "Start";
        return (
            <>
                <Button variant="ghost" className="ios-btn--sm" onClick={() => this.attemptToStart(row)}>{startLabel}</Button>
                <Button variant="danger" className="ios-btn--sm" onClick={() => this.attemptToStop(row)}>Stop</Button>
            </>
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

    paymentDetailsDialog() {
        const r = this.state.tx_details_row;
        const beneficiaries = r.beneficiaries || [];
        const benColumns = [
            { key: 'rn', header: '#', width: 44, render: (row, i) => i + 1 },
            { key: 'name', header: 'Name', accessor: (row) => row.name },
            { key: 'account', header: 'Account', accessor: (row) => row.account },
            { key: 'amount', header: 'Amount', numeric: true, accessor: (row) => row.amount },
            { key: 'beneficiary_status', header: 'Status', accessor: (row) => row.beneficiary_status },
        ];
        return (
            <Sheet
                open={this.state.detailsOpen}
                onClose={() => this.setState({ detailsOpen: false })}
                title={"Payment Details: " + (r.name || '')}
                size="lg"
                footer={<Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ detailsOpen: false })}>{strings.close}</Button>}
            >
                {this.detailRow('Name', r.name)}
                {this.detailRow('Description', <span dangerouslySetInnerHTML={{ __html: r.tx_description ? common.encodeHTML(r.tx_description).replace(/\n/g, "<br/>") : "" }} />)}
                {this.detailRow('Payment ID', r.batch_id)}
                {this.detailRow('Status', <Badge tone={statusTone(r.status)}>{r.status}</Badge>)}
                {this.detailRow('Total Amount', "UGX " + (r.total_amount || ''))}
                {this.detailRow('Created By', r.created_by)}
                {this.detailRow('Created On', r.created_on)}
                {this.detailRow('Total Paid', `${r.total_paid}/${r.total_beneficiaries}`)}
                <h3 className="cpay-sheet-section-title">Beneficiaries</h3>
                <Table columns={benColumns} rows={beneficiaries} rowKey={(row, i) => row.id ?? i} emptyText="No beneficiaries." />
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
            { key: 'name', header: 'Name', accessor: (r) => r.name, sortable: true, sortValue: (r) => r.name || '' },
            { key: 'tx_description', header: 'Description', accessor: (r) => r.tx_description },
            { key: 'paid', header: 'Paid', render: (r) => `${r.total_paid}/${r.total_beneficiaries}` },
            { key: 'status', header: 'Status', render: (r) => <Badge tone={statusTone(r.status)}>{r.status}</Badge>, sortable: true, sortValue: (r) => r.status || '' },
            { key: 'total_amount', header: 'Total Amount', numeric: true, accessor: (r) => r.total_amount },
            {
                key: 'actions', header: 'Actions', align: 'center',
                render: (row) => (
                    <span className="ios-cell-actions">
                        <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ tx_details_row: row, detailsOpen: true })}>{strings.details}</Button>
                        <Button variant="ghost" className="ios-btn--sm" onClick={() => this.editRow(row)}>Edit</Button>
                        {this.returnPaymentAction(row)}
                    </span>
                ),
            },
        ];

        return (
            <Card flush>
                <div style={{ padding: 'var(--ios-space-4)' }}>
                    <Toolbar>
                        <Button variant="primary" className="ios-btn--sm" onClick={() => this.addNew()}>
                            <Icons.PlusIcon size={16} />{strings.add_payment}
                        </Button>
                        <Toolbar.Spacer />
                        <div style={{ minWidth: 150 }}>
                            <Select id="pay-category" value={searchingValue.category} options={SEARCH_CATEGORIES} onValueChange={(v) => this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, category: v } }))} />
                        </div>
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
                    rowKey={(row, i) => row.id ?? row.batch_id ?? i}
                    pageSize={this.state.pageSize}
                    isRowSelected={(row) => Boolean(row.selected)}
                    emptyText="No payments to display."
                />
                {this.paymentDetailsDialog()}
                <PaymentFormDialog
                    open={this.state.formOpen}
                    loader={this.props.loader}
                    getMessager={() => this.messager}
                    onClose={() => this.setState({ formOpen: false })}
                    formd={this.state.formd}
                    title={this.state.title}
                    formdMode={this.state.formdMode}
                    saveRow={(payload) => this.saveRow(payload)}
                />
                <Messager ref={ref => this.messager = ref}></Messager>
            </Card>
        );
    }
}

/** Add / edit payment with an inline-editable beneficiaries grid + Excel upload. */
class PaymentFormDialog extends React.Component {
    constructor(props) {
        super(props);
        this.state = { formd: props.formd, all_fields_amount: "1000" };
    }

    componentDidUpdate(prevProps) {
        if (prevProps.formd !== this.props.formd) {
            this.setState({ formd: this.props.formd });
        }
    }

    setField(name, value) {
        this.setState((prev) => ({ formd: { ...prev.formd, [name]: value } }));
    }

    updateBeneficiary(index, field, value) {
        this.setState((prev) => {
            const beneficiaries = (prev.formd.beneficiaries || []).map((b, i) => (i === index ? { ...b, [field]: value } : b));
            return { formd: { ...prev.formd, beneficiaries } };
        });
    }

    addBeneficiary() {
        this.setState((prev) => ({
            formd: {
                ...prev.formd,
                beneficiaries: [
                    ...(prev.formd.beneficiaries || []),
                    { name: "", account: "", amount: this.state.all_fields_amount, account_type: "phone", status: "ACTIVE", delete: false, id: "" },
                ],
            },
        }));
    }

    removeLastBeneficiary() {
        this.setState((prev) => {
            const list = (prev.formd.beneficiaries || []).slice(0, -1);
            return { formd: { ...prev.formd, beneficiaries: list } };
        });
    }

    removeAllBeneficiaries() {
        this.setState((prev) => ({ formd: { ...prev.formd, beneficiaries: [] } }));
    }

    applyAmountToAll() {
        this.setState((prev) => ({
            formd: { ...prev.formd, beneficiaries: (prev.formd.beneficiaries || []).map((b) => ({ ...b, amount: this.state.all_fields_amount })) },
        }));
    }

    uploadBeneficiaries(files) {
        const form = new FormData();
        for (let i = 0; i < files.length; i++) form.append('file', files[i]);
        this.props.loader("START");
        fetch(common.base_url + "/transactions/uploadBeneficiariesFile", { method: 'POST', mode: 'cors', credentials: 'include', body: form })
            .then((r) => r.text()).then((t) => {
                this.props.loader("STOP");
                const messager = this.props.getMessager();
                let r;
                try { r = JSON.parse(t); } catch { if (messager) messager.alert({ title: "Error", icon: "error", msg: "Invalid upload response" }); return; }
                if (r.state === "ERROR") {
                    if (messager) messager.alert({ title: "Error", icon: "error", msg: r.message });
                } else if (r.state === "OK") {
                    const rows = (r.data || []).map((d) => ({ ...d, account: d.account + "" }));
                    this.setState((prev) => ({ formd: { ...prev.formd, beneficiaries: [...(prev.formd.beneficiaries || []), ...rows] } }));
                }
            }).catch((e) => {
                this.props.loader("STOP");
                const messager = this.props.getMessager();
                if (messager) messager.alert({ title: "Error", icon: "error", msg: e.message });
            });
    }

    submit() {
        this.props.saveRow(this.state.formd);
    }

    close() {
        this.setState({ formd: { beneficiaries: [], name: "", tx_description: "", delete: false } }, () => this.props.onClose());
    }

    render() {
        const { open, title } = this.props;
        const row = this.state.formd || { beneficiaries: [] };
        const beneficiaries = row.beneficiaries || [];
        const accountTypeOptions = toOptions(common.account_types);

        const benColumns = [
            { key: 'rn', header: '#', width: 40, render: (b, i) => i + 1 },
            { key: 'name', header: 'Name', render: (b, i) => <TextField id={`ben-name-${i}`} label="" value={b.name || ''} onValueChange={(v) => this.updateBeneficiary(i, 'name', v)} /> },
            { key: 'account', header: 'Account', render: (b, i) => <TextField id={`ben-acct-${i}`} label="" value={b.account || ''} onValueChange={(v) => this.updateBeneficiary(i, 'account', v)} /> },
            { key: 'amount', header: 'Amount', render: (b, i) => <TextField id={`ben-amt-${i}`} label="" value={String(b.amount ?? '')} onValueChange={(v) => this.updateBeneficiary(i, 'amount', v)} /> },
            { key: 'account_type', header: 'Account Type', render: (b, i) => <Select id={`ben-type-${i}`} value={b.account_type || 'phone'} options={accountTypeOptions} onValueChange={(v) => this.updateBeneficiary(i, 'account_type', v)} /> },
            { key: 'delete', header: strings.delete, align: 'center', render: (b, i) => <Checkbox checked={Boolean(b.delete)} onCheckedChange={(c) => this.updateBeneficiary(i, 'delete', c)} /> },
        ];

        return (
            <Sheet
                open={open}
                onClose={() => this.close()}
                title={title}
                size="xl"
                footer={<>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.close()}>Close</Button>
                    <Button variant="primary" className="ios-btn--sm" onClick={() => this.submit()}>{strings.submit}</Button>
                </>}
            >
                <div className="ios-form">
                    <TextField id="pay-name" label="Name" value={row.name || ''} onValueChange={(v) => this.setField('name', v)} />
                    <TextArea id="pay-desc" label="Description" rows={2} value={row.tx_description || ''} onValueChange={(v) => this.setField('tx_description', v)} />
                </div>

                <h3 className="ios-section-title" style={{ marginTop: 'var(--ios-space-5)' }}>Beneficiaries</h3>
                <Toolbar>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.addBeneficiary()}>{strings.add_beneficiary}</Button>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.removeLastBeneficiary()}>{strings.remove_beneficiary}</Button>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.removeAllBeneficiaries()}>{strings.remove_all_rows}</Button>
                    <FileButton accept=".xls,.xlsx,.csv" onFiles={(files) => this.uploadBeneficiaries(files)}>{strings.upload_excel_file}</FileButton>
                    <Toolbar.Spacer />
                    <div style={{ width: 140 }}>
                        <TextField id="pay-all-amount" label="" placeholder="Amount" value={this.state.all_fields_amount} onValueChange={(v) => this.setState({ all_fields_amount: v })} />
                    </div>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.applyAmountToAll()}>Apply Amount</Button>
                </Toolbar>
                <div style={{ marginTop: 'var(--ios-space-3)' }}>
                    <Table columns={benColumns} rows={beneficiaries} rowKey={(b, i) => i} pageSize={50} emptyText="No beneficiaries yet — add or upload." />
                </div>
            </Sheet>
        );
    }
}

const MerchantModulePayments = withRouter(MerchantModulePaymentsC);

export default MerchantModulePayments;
