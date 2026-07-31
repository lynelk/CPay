import React from 'react';
import Messager from '../../StableMessager';
import { withRouter } from '../../../shared/router/compat';
import { Card, Toolbar, Table, Select, SearchField, Checkbox, Badge } from '../../../ui';

import { apiFetch } from '../../../shared/api/httpClient';
import { apiUrl } from '../../../shared/config';

const CATEGORIES = [
  { value: 'all', label: 'All Fields' },
  { value: 'user_id', label: 'User ID' },
  { value: 'action', label: 'Action' },
  { value: 'user_name', label: 'User Name' },
];

class MerchantModuleAuditTrailC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            hasAccess: false,
            total: 0,
            pageSize: 50,
            allChecked: false,
            data: [],
            searchingValue: { value: '', category: 'all' },
        };
    }

    componentDidMount() {
        this.getData();
    }

    getData() {
        this.props.loader("START");
        const searchData = {
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        };
        apiFetch(apiUrl("/audittrail/getMerchantAudittrails"), {
            method: 'POST',
            mode: 'cors',
            cache: 'no-cache',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            redirect: 'follow',
            referrer: 'no-referrer',
            body: JSON.stringify(searchData)
        }).then((response) => response.text()).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.setState({ hasAccess: true, data: res.data, total: res.data.length, allChecked: false });
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
            this.messager.alert({ title: "Error", icon: "error", msg: error.message });
        });
    }

    sessionExpired() {
        const { history } = this.props;
        this.messager.alert({ title: "Session Expired!", icon: "info", msg: "Your session expired", result: () => history.push("/portal") });
    }

    accessNotAllowed(msg) {
        this.messager.alert({ title: "Access denied!", icon: "info", msg, result: () => this.setState({ hasAccess: false }) });
    }

    handleSearch(value) {
        this.setState(
            (prev) => ({ searchingValue: { ...prev.searchingValue, value } }),
            () => this.getData(),
        );
    }

    handleCategory(category) {
        this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, category } }));
    }

    handleRowCheck(row, checked) {
        const data = this.state.data.map((r) => (r === row ? { ...r, selected: checked } : r));
        this.setState({ data, allChecked: data.every((r) => r.selected) });
    }

    handleAllCheck(checked) {
        this.setState({ allChecked: checked, data: this.state.data.map((r) => ({ ...r, selected: checked })) });
    }

    render() {
        const { searchingValue, data, allChecked } = this.state;
        if (!this.state.hasAccess) {
            return <div><Messager ref={ref => this.messager = ref}></Messager></div>;
        }

        const columns = [
            {
                key: 'ck',
                width: 44,
                header: <Checkbox checked={allChecked} onCheckedChange={(c) => this.handleAllCheck(c)} />,
                render: (row) => (
                    <Checkbox checked={Boolean(row.selected)} onCheckedChange={(c) => this.handleRowCheck(row, c)} />
                ),
            },
            { key: 'created_on', header: 'Created On', accessor: (r) => r.created_on, sortable: true, sortValue: (r) => r.created_on || '', width: 190 },
            { key: 'user_id', header: 'User ID', accessor: (r) => r.user_id, sortable: true, sortValue: (r) => r.user_id || '', width: 150 },
            { key: 'user_name', header: 'User Name', accessor: (r) => r.user_name, sortable: true, sortValue: (r) => r.user_name || '', width: 160 },
            { key: 'action', header: 'Action', render: (r) => <Badge tone="info">{r.action}</Badge> },
        ];

        return (
            <Card flush>
                <div style={{ padding: 'var(--ios-space-4)' }}>
                    <Toolbar>
                        <div style={{ minWidth: 200 }}>
                            <Select
                                id="merchant-audit-category"
                                value={searchingValue.category}
                                options={CATEGORIES}
                                onValueChange={(v) => this.handleCategory(v)}
                            />
                        </div>
                        <Toolbar.Spacer />
                        <SearchField
                            value={searchingValue.value}
                            onValueChange={(v) => this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, value: v } }))}
                            onSubmit={(v) => this.handleSearch(v)}
                            placeholder="Search audit trail"
                        />
                    </Toolbar>
                </div>
                <Table
                    columns={columns}
                    rows={data}
                    rowKey={(row, i) => row.id ?? `${row.user_id}-${row.created_on}-${i}`}
                    pageSize={this.state.pageSize}
                    isRowSelected={(row) => Boolean(row.selected)}
                    renderDetail={(row) => (
                        <div className="ios-grid">
                            <div><strong>Created On:</strong> {row.created_on}</div>
                            <div><strong>Action:</strong> {row.action}</div>
                        </div>
                    )}
                    emptyText="No audit records to display."
                />
                <Messager ref={ref => this.messager = ref}></Messager>
            </Card>
        );
    }
}

const MerchantModuleAuditTrail = withRouter(MerchantModuleAuditTrailC);

export default MerchantModuleAuditTrail;
