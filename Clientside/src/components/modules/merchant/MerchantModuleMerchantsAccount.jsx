import React from 'react';
import Messager from '../../StableMessager';
import { withRouter } from '../../../shared/router/compat';
import common from "../../Common";
import strings from '../../locale';
import ReactExport from "../../../shared/export/ExcelExport";
import { Card, Toolbar, Table, Sheet, Button, DateField, Icons } from '../../../ui';

import { apiFetch } from '../../../shared/api/httpClient';

const ExcelFile = ReactExport.ExcelFile;
const ExcelSheet = ReactExport.ExcelFile.ExcelSheet;
const ExcelColumn = ReactExport.ExcelFile.ExcelColumn;

class MerchantModuleMerchantAccouuntC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            pageSize: 50,
            data: [],
            total: 0,
            searchingValue: { value: "", category: "all" },
            search_rules: { start_date: "", end_date: "" },
            searchOpen: false,
            available_balances: "",
        };
    }

    componentDidMount() {
        this.getData();
    }

    messagerAlert(opts) {
        const m = this.props.messager || this.messager;
        if (m) m.alert(opts);
    }

    getData() {
        this.props.loader("START");
        const searchData = {
            search_rules: {
                start_date: this.state.search_rules.start_date || "",
                end_date: this.state.search_rules.end_date || "",
            },
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        };
        apiFetch(common.base_url + "/transactions/getMerchantStatementByMerchant", {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify(searchData)
        }).then((response) => response.text()).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.setState({ data: res.data, total: res.total, available_balances: res.balances });
                } else {
                    if (res.code === "107") { this.props.sessionExpired(); return; }
                    if (res.code === "110") { this.props.accessNotAllowed(res.message); return; }
                    this.messagerAlert({ title: "Error " + res.code, icon: "error", msg: res.message });
                }
            } catch (Error) {
                this.messagerAlert({ title: "Error", icon: "error", msg: Error.message });
            }
        }).catch((error) => {
            this.props.loader("STOP");
            this.messagerAlert({ title: "Error", icon: "error", msg: error.message });
        });
    }

    handleSearchFormChange(name, value) {
        this.setState((prev) => ({ search_rules: { ...prev.search_rules, [name]: value } }));
    }

    clearSearch() {
        this.setState({ search_rules: { start_date: "", end_date: "" } });
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
                    <DateField id="mstmt-start" label="Start Date" kind="date" value={s.start_date} onValueChange={(v) => this.handleSearchFormChange('start_date', v)} />
                    <DateField id="mstmt-end" label="End Date" kind="date" value={s.end_date} onValueChange={(v) => this.handleSearchFormChange('end_date', v)} />
                </div>
            </Sheet>
        );
    }

    render() {
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
            <Card flush>
                <div style={{ padding: 'var(--ios-space-4)' }}>
                    <Toolbar>
                        <span style={{ color: 'var(--ios-text-secondary)', fontSize: 'var(--ios-fs-footnote)' }}>Available Balances:</span>
                        <strong style={{ color: 'var(--ios-success)' }}>{this.state.available_balances}</strong>
                        <Toolbar.Spacer />
                        <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ searchOpen: true })}>
                            <Icons.SearchIcon size={16} />{strings.search}
                        </Button>
                        <Download data={this.state.data} />
                    </Toolbar>
                </div>
                <Table
                    columns={columns}
                    rows={this.state.data}
                    rowKey={(row, i) => row.id ?? i}
                    pageSize={this.state.pageSize}
                    emptyText="No statement entries to display."
                />
                {this.searchSheet()}
                <Messager ref={ref => this.messager = ref}></Messager>
            </Card>
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
                    <ExcelColumn label="Account" value="payer_number" />
                    <ExcelColumn label="Amount" value="amount" />
                    <ExcelColumn label="MTN MM BAL" value="mtnmm_balance" />
                    <ExcelColumn label="AIRTEL MM BAL" value="airtelmm_balance" />
                    <ExcelColumn label="SMS BAL" value="sms_balance" />
                </ExcelSheet>
            </ExcelFile>
        );
    }
}

const MerchantModuleMerchantAccouunt = withRouter(MerchantModuleMerchantAccouuntC);

export default MerchantModuleMerchantAccouunt;
