import React from 'react';
import Messager from '../StableMessager';
import { withRouter } from '../../shared/router/compat';
import common from "../Common";
import strings from '../locale';
import {
  Card, Toolbar, Table, Select, SearchField, Checkbox, Badge, Sheet, Button,
  TextField, PasswordField, Icons,
} from '../../ui';

import { apiFetch } from '../../shared/api/httpClient';
import { apiUrl } from '../../shared/config';
import { readStoredUser } from '../../shared/useAuth';

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'ACTIVE' },
  { value: 'INACTIVE', label: 'INACTIVE' },
  { value: 'SUSPENDED', label: 'SUSPENDED' },
];

const CATEGORIES = [
  { value: 'all', label: 'All Fields' },
  { value: 'email', label: 'Email' },
  { value: 'status', label: 'Status' },
  { value: 'phone', label: 'Phone' },
  { value: 'name', label: 'Name' },
];

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function statusTone(status) {
  const v = typeof status === 'object' && status ? status.value : status;
  if (v === 'ACTIVE') return 'success';
  if (v === 'SUSPENDED') return 'danger';
  return 'neutral';
}
function statusText(status) {
  return typeof status === 'object' && status ? status.value : status;
}
function privilegeValue(p) {
  if (p == null) return '';
  if (typeof p === 'object') return p.value ?? p.privilege ?? '';
  return p;
}

class ModuleAdminsC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            total: 0,
            pageSize: 50,
            allChecked: false,
            data: [],
            formdMode: 'new',
            formd: { id: "", email: "", name: "", phone: "", password: "", privileges: [], status: 'ACTIVE' },
            privileges: common.privileges || [],
            errors: {},
            title: '',
            dialogOpen: false,
            searchingValue: { value: "", category: "all" },
            hasAccess: false,
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
        const user = readStoredUser('admin');
        if (user.privileges) {
            for (let i = 0; i < user.privileges.length; i++) {
                const p = user.privileges[i].privilege;
                if (p === "CREATE_ADMIN" || p === "UPDATE_ADMIN" || p === "DETETE_ADMIN") return true;
            }
        }
        return false;
    }

    resetForm(after) {
        this.setState({
            formd: { id: "", email: "", name: "", phone: "", password: "", privileges: [], status: 'ACTIVE' },
            errors: {},
        }, after);
    }

    getData() {
        this.props.loader("START");
        const searchData = { pageSize: this.state.pageSize, searchingValue: this.state.searchingValue, sort: 'asc' };
        apiFetch(apiUrl("/admins/getAdmins"), {
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
            this.messager.alert({ title: "Error", icon: "error", msg: error.message });
        });
    }

    accessNotAllowed(msg) {
        this.messager.alert({ title: "Access denied!", icon: "info", msg, result: () => this.setState({ hasAccess: false }) });
    }

    sessionExpired() {
        const { history } = this.props;
        this.messager.alert({ title: "Session Expired!", icon: "info", msg: "Your session expired", result: () => history.push("/") });
    }

    editRow(row) {
        const formd = { ...row, password: "", privileges: (row.privileges || []).map(privilegeValue) };
        this.setState({ formdMode: 'edit', formd, dialogOpen: true, errors: {}, title: "Edit Admin (" + row.name + ")" });
    }

    deleteRow(row) {
        this.messager.confirm({
            title: "Delete this User", icon: "info", msg: "Are you sure you want to delete this user?",
            result: (r) => {
                if (!r) return;
                this.props.loader("START");
                apiFetch(apiUrl("/admins/deleteAdmin"), {
                    method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
                    body: JSON.stringify(row)
                }).then((response) => response.text()).then((response_) => {
                    this.props.loader("STOP");
                    let res;
                    try {
                        res = JSON.parse(response_);
                        if (res.code === "000") {
                            this.resetForm(() => this.messager.alert({
                                title: "Success!", icon: "info", msg: res.message,
                                result: (ok) => { if (ok) this.getData(); }
                            }));
                        } else {
                            if (res.code === "107") { this.sessionExpired(); return; }
                            this.messager.alert({
                                title: "Error " + (res.code ? res.code : res.status + " " + res.error),
                                icon: "error", msg: res.message + ". Error: " + res.error,
                            });
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

    addNew() {
        this.resetForm(() => this.setState({ formdMode: 'new', dialogOpen: true, title: "Add new Admin" }));
    }

    handleSearch(value) {
        this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, value } }), () => this.getData());
    }

    handleFormChange(name, value) {
        this.setState((prev) => ({ formd: { ...prev.formd, [name]: value } }));
    }

    togglePrivilege(value, checked) {
        this.setState((prev) => {
            const current = new Set((prev.formd.privileges || []).map(privilegeValue));
            if (checked) current.add(value); else current.delete(value);
            return { formd: { ...prev.formd, privileges: Array.from(current) } };
        });
    }

    validate() {
        const f = this.state.formd;
        const errors = {};
        if (!f.name) errors.name = 'Name is required';
        if (!statusText(f.status)) errors.status = 'Status is required';
        if (!f.email) errors.email = 'Email is required';
        else if (!EMAIL_RE.test(f.email)) errors.email = 'Enter a valid email address';
        if (!f.phone) errors.phone = 'Phone is required';
        this.setState({ errors });
        return Object.keys(errors).length === 0;
    }

    saveRow() {
        if (!this.validate()) return;
        const url = this.state.formdMode === "edit"
            ? apiUrl("/admins/editAdmin")
            : apiUrl("/admins/addAdmin");
        this.props.loader("START");
        apiFetch(url, {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify(this.state.formd)
        }).then((response) => response.text()).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.resetForm(() => this.messager.alert({
                        title: "Success!", icon: "info", msg: res.message,
                        result: (ok) => { if (ok) this.setState({ dialogOpen: false }, () => this.getData()); }
                    }));
                } else {
                    if (res.code === "107") { this.sessionExpired(); return; }
                    this.messager.alert({
                        title: "Error " + (res.code ? res.code : res.status + " " + res.error),
                        icon: "error", msg: res.message + ". Error: " + res.error,
                    });
                }
            } catch (Error) {
                this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
            }
        }).catch((error) => {
            this.props.loader("STOP");
            this.messager.alert({ title: "Error", icon: "error", msg: error.message });
        });
    }

    closeDialog() {
        this.resetForm(() => this.setState({ dialogOpen: false }));
    }

    handleRowCheck(row, checked) {
        const data = this.state.data.map((r) => (r === row ? { ...r, selected: checked } : r));
        this.setState({ data, allChecked: data.every((r) => r.selected) });
    }

    handleAllCheck(checked) {
        this.setState({ allChecked: checked, data: this.state.data.map((r) => ({ ...r, selected: checked })) });
    }

    renderDialog() {
        const { formd, errors, privileges, dialogOpen, title } = this.state;
        const selected = new Set((formd.privileges || []).map(privilegeValue));
        return (
            <Sheet
                open={dialogOpen}
                onClose={() => this.closeDialog()}
                title={title || 'Admin'}
                size="md"
                footer={<>
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.closeDialog()}>Close</Button>
                    <Button variant="primary" className="ios-btn--sm" onClick={() => this.saveRow()}>Save</Button>
                </>}
            >
                <div className="ios-form">
                    <TextField id="admin-name" label="Name" value={formd.name || ''} invalid={Boolean(errors.name)} onValueChange={(v) => this.handleFormChange('name', v)} />
                    {errors.name ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{errors.name}</span> : null}
                    <TextField id="admin-phone" label="Phone" value={formd.phone || ''} invalid={Boolean(errors.phone)} onValueChange={(v) => this.handleFormChange('phone', v)} />
                    {errors.phone ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{errors.phone}</span> : null}
                    <TextField id="admin-email" label="Email" type="email" value={formd.email || ''} invalid={Boolean(errors.email)} onValueChange={(v) => this.handleFormChange('email', v)} />
                    {errors.email ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{errors.email}</span> : null}
                    <PasswordField id="admin-password" label="User Password" value={formd.password || ''} onValueChange={(v) => this.handleFormChange('password', v)} />
                    <Select id="admin-status" label="Status" value={statusText(formd.status) || 'ACTIVE'} options={STATUS_OPTIONS} onValueChange={(v) => this.handleFormChange('status', v)} />
                    <div className="ios-field">
                        <span className="ios-field__label">Privileges</span>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
                            {privileges.map((p) => (
                                <Checkbox
                                    key={p.value}
                                    checked={selected.has(p.value)}
                                    onCheckedChange={(c) => this.togglePrivilege(p.value, c)}
                                    label={p.text}
                                />
                            ))}
                        </div>
                    </div>
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
            { key: 'name', header: 'Name', accessor: (r) => r.name, sortable: true, sortValue: (r) => r.name || '' },
            { key: 'email', header: 'Email', accessor: (r) => r.email, sortable: true, sortValue: (r) => r.email || '' },
            { key: 'phone', header: 'Phone', accessor: (r) => r.phone },
            { key: 'status', header: 'Status', render: (r) => <Badge tone={statusTone(r.status)}>{statusText(r.status)}</Badge>, sortable: true, sortValue: (r) => statusText(r.status) || '' },
            {
                key: 'actions', header: 'Actions', align: 'center',
                render: (row) => (
                    <span className="ios-cell-actions">
                        <Button variant="ghost" className="ios-btn--sm" onClick={() => this.editRow(row)}>Edit</Button>
                        <Button variant="danger" className="ios-btn--sm" onClick={() => this.deleteRow(row)}>Delete</Button>
                    </span>
                ),
            },
        ];

        return (
            <Card flush>
                <div style={{ padding: 'var(--ios-space-4)' }}>
                    <Toolbar>
                        <Button variant="primary" className="ios-btn--sm" onClick={() => this.addNew()}>
                            <Icons.PlusIcon size={16} />{strings.add_admin}
                        </Button>
                        <Toolbar.Spacer />
                        <div style={{ minWidth: 160 }}>
                            <Select id="admin-category" value={searchingValue.category} options={CATEGORIES} onValueChange={(v) => this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, category: v } }))} />
                        </div>
                        <SearchField
                            value={searchingValue.value}
                            onValueChange={(v) => this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, value: v } }))}
                            onSubmit={(v) => this.handleSearch(v)}
                            placeholder={strings.search_admin}
                        />
                    </Toolbar>
                </div>
                <Table
                    columns={columns}
                    rows={data}
                    rowKey={(row, i) => row.id ?? `${row.email}-${i}`}
                    pageSize={this.state.pageSize}
                    isRowSelected={(row) => Boolean(row.selected)}
                    emptyText="No administrators to display."
                />
                <Messager ref={ref => this.messager = ref}></Messager>
                {this.renderDialog()}
            </Card>
        );
    }
}

const ModuleAdmins = withRouter(ModuleAdminsC);

export default ModuleAdmins;
