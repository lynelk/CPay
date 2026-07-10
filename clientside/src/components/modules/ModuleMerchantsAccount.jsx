import React from 'react';
import common from "../Common";
import strings from '../locale';
import ReactExport from "../../shared/export/ExcelExport";
import {
  Toolbar, Table, Select, Sheet, Button, TextField, TextArea, DateField, Icons,
} from '../../ui';

const ExcelFile = ReactExport.ExcelFile;
const ExcelSheet = ReactExport.ExcelFile.ExcelSheet;
const ExcelColumn = ReactExport.ExcelFile.ExcelColumn;

const toOptions = (arr) => (Array.isArray(arr) ? arr.map((t) => ({ value: t.value, label: t.text })) : []);

/**
 * Merchant account statement dialog. Rendered by ModuleMerchants; visibility is
 * driven by props.statementDialogStateOpened (true = closed) +
 * props.openOrCloseStatementDialog. Migrated off rc-easyui to iOS Sheets/Table.
 */
class ModuleMerchantAccouunt extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            pageSize: 50,
            data: [],
            total: 0,
            searchingValue: { value: "", category: "all" },
            search_rules: { start_date: "", end_date: "" },
            searchOpen: false,
            record_tx_data: { tx_type: "", amount: "", description: "", balance_type: "" },
            recordTxOpen: false,
            recordTxErrors: {},
            available_balances: "",
        };
    }

    componentDidUpdate(prevProps) {
        // Fetch when the statement dialog transitions from closed -> open.
        if (prevProps.statementDialogStateOpened && !this.props.statementDialogStateOpened) {
            this.getData();
        }
    }

    getData() {
        this.props.loader("START");
        const searchData = {
            search_rules: {
                start_date: this.state.search_rules.start_date || "",
                end_date: this.state.search_rules.end_date || "",
            },
            merchant_id: this.props.openMerchantAccount.id ? this.props.openMerchantAccount.id : null,
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        };
        const url = this.props.openMerchantAccount.id
            ? common.base_url + "/transactions/getMerchantStatement"
            : common.base_url + "/transactions/getMerchantStatementByMerchant";

        fetch(url, {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify(searchData)
        }).then((response) => response.text()).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.setState({ data: res.data, total: res.data.length, available_balances: res.balances });
                } else {
                    if (res.code === "107") { this.props.sessionExpired(); return; }
                    if (res.code === "110") { this.props.accessNotAllowed(res.message); return; }
                    this.props.messager.alert({ title: "Error " + res.code, icon: "error", msg: res.message });
                }
            } catch (Error) {
                this.props.messager.alert({ title: "Error", icon: "error", msg: Error.message });
            }
        }).catch((error) => {
            this.props.loader("STOP");
            this.props.messager.alert({ title: "Error", icon: "error", msg: error.message });
        });
    }

    recordTransactionRequest() {
        this.props.loader("START");
        const data = { ...this.state.record_tx_data, merchant_id: this.props.openMerchantAccount.id };
        fetch(common.base_url + "/transactions/recordTransaction", {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify(data)
        }).then((response) => response.text()).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.resetRecordTxForm(() => {
                        this.setState({ recordTxOpen: false });
                        this.props.messager.alert({
                            title: "Success!", icon: "info", msg: res.message,
                            result: (ok) => { if (ok) this.getData(); }
                        });
                    });
                } else {
                    if (res.code === "107") { this.props.sessionExpired(); return; }
                    if (res.code === "110") { this.props.accessNotAllowed(res.message); return; }
                    this.props.messager.alert({ title: "Error " + res.code, icon: "error", msg: res.message });
                }
            } catch (Error) {
                this.props.messager.alert({ title: "Error", icon: "error", msg: Error.message });
            }
        }).catch((error) => {
            this.props.loader("STOP");
            this.props.messager.alert({ title: "Error", icon: "error", msg: error.message });
        });
    }

    recordTxSubmit() {
        const f = this.state.record_tx_data;
        const errors = {};
        if (!f.tx_type) errors.tx_type = 'Transaction type is required';
        if (!f.description) errors.description = 'Description is required';
        if (f.amount === '' || Number.isNaN(Number(f.amount))) errors.amount = 'Enter a valid amount';
        this.setState({ recordTxErrors: errors });
        if (Object.keys(errors).length === 0) this.recordTransactionRequest();
    }

    handleSearchFormChange(name, value) {
        this.setState((prev) => ({ search_rules: { ...prev.search_rules, [name]: value } }));
    }

    handleRecordFormChange(name, value) {
        this.setState((prev) => ({ record_tx_data: { ...prev.record_tx_data, [name]: value } }));
    }

    clearSearch() {
        this.setState({ search_rules: { start_date: "", end_date: "" } });
    }

    resetRecordTxForm(whenDone) {
        this.setState({ record_tx_data: { tx_type: "", amount: "", description: "", balance_type: "" }, recordTxErrors: {} }, whenDone);
    }

    searchSheet() {
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
                    <DateField id="stmt-start" label="Start Date" kind="date" value={s.start_date} onValueChange={(v) => this.handleSearchFormChange('start_date', v)} />
                    <DateField id="stmt-end" label="End Date" kind="date" value={s.end_date} onValueChange={(v) => this.handleSearchFormChange('end_date', v)} />
                </div>
            </Sheet>
        );
    }

    recordTxSheet() {
        const { record_tx_data: f, recordTxErrors: e } = this.state;
        return (
            <Sheet
                open={this.state.recordTxOpen}
                onClose={() => this.resetRecordTxForm(() => this.setState({ recordTxOpen: false }))}
                title={strings.record_tx}
                size="sm"
                footer={<>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.resetRecordTxForm(() => this.setState({ recordTxOpen: false }))}>{strings.close}</Button>
                    <Button variant="primary" className="ios-btn--sm" onClick={() => this.recordTxSubmit()}>{strings.save}</Button>
                </>}
            >
                <div className="ios-form">
                    <Select id="rtx-balance" label="Balance Type" value={f.balance_type} placeholder="Select" options={toOptions(common.balance_type)} onValueChange={(v) => this.handleRecordFormChange('balance_type', v)} />
                    <Select id="rtx-type" label="Transaction Type" value={f.tx_type} placeholder="Select" options={toOptions(common.tx_types)} onValueChange={(v) => this.handleRecordFormChange('tx_type', v)} />
                    {e.tx_type ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{e.tx_type}</span> : null}
                    <TextField id="rtx-amount" label="Amount" value={f.amount} invalid={Boolean(e.amount)} onValueChange={(v) => this.handleRecordFormChange('amount', v)} />
                    {e.amount ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{e.amount}</span> : null}
                    <TextArea id="rtx-desc" label="Description" rows={3} value={f.description} invalid={Boolean(e.description)} onValueChange={(v) => this.handleRecordFormChange('description', v)} />
                    {e.description ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{e.description}</span> : null}
                </div>
            </Sheet>
        );
    }

    render() {
        const { title, statementDialogStateOpened } = this.props;
        const columns = [
            { key: 'rownum', header: '#', width: 44, render: (row, i) => i + 1 },
            { key: 'created_on', header: 'Created On', accessor: (r) => r.created_on, width: 170 },
            { key: 'description', header: 'Description', render: (r) => `${r.narrative}: ${r.description}` },
            {
                key: 'amount', header: 'Amount', numeric: true,
                render: (r) => (
                    <span style={{ color: r.tx_type === 'CR' ? 'var(--ios-success)' : 'var(--ios-danger)', fontWeight: 600 }}>
                        {common.formatNumber(r.amount)}
                    </span>
                ),
            },
            { key: 'balances', header: 'Balance', numeric: true, accessor: (r) => r.balances },
        ];

        return (
            <>
                <Sheet
                    open={!statementDialogStateOpened}
                    onClose={() => this.props.openOrCloseStatementDialog(true)}
                    title={title}
                    size="xl"
                    footer={<>
                        <Button variant="primary" className="ios-btn--sm" onClick={() => this.setState({ recordTxOpen: true })}>
                            <Icons.PaymentsIcon size={16} />{strings.record_tx}
                        </Button>
                        <Button variant="ghost" className="ios-btn--sm" onClick={() => this.props.openOrCloseStatementDialog(true)}>Close</Button>
                    </>}
                >
                    <Toolbar>
                        <span style={{ color: 'var(--ios-text-secondary)', fontSize: 'var(--ios-fs-footnote)' }}>Available Balances:</span>
                        <strong style={{ color: 'var(--ios-success)' }}>{this.state.available_balances}</strong>
                        <Toolbar.Spacer />
                        <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ searchOpen: true })}>
                            <Icons.SearchIcon size={16} />{strings.search}
                        </Button>
                        <Download data={this.state.data} />
                    </Toolbar>
                    <div style={{ marginTop: 'var(--ios-space-4)' }}>
                        <Table
                            columns={columns}
                            rows={this.state.data}
                            rowKey={(row, i) => row.id ?? i}
                            pageSize={this.state.pageSize}
                            emptyText="No statement entries to display."
                        />
                    </div>
                </Sheet>
                {this.searchSheet()}
                {this.recordTxSheet()}
            </>
        );
    }
}

class Download extends React.Component {
    render() {
        return (
            <ExcelFile
                filename="Account_Statement"
                ref={ref => this.excelRef = ref}
                element={
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.excelRef.download()}>
                        <Icons.DownloadIcon size={16} />{strings.download}
                    </Button>
                }>
                <ExcelSheet data={this.props.data} name="Statement">
                    <ExcelColumn label="Date time" value="created_on" />
                    <ExcelColumn label="Description" value="description" />
                    <ExcelColumn label="Amount" value="amount" />
                    <ExcelColumn label="Balances" value={"balances"} />
                </ExcelSheet>
            </ExcelFile>
        );
    }
}

export default ModuleMerchantAccouunt;
