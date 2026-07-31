import React from 'react';
import Messager from '../StableMessager';
import { withRouter } from '../../shared/router/compat';
import common from '../Common';
import strings from '../locale';
import ModuleMerchantsAccount from './ModuleMerchantsAccount';
import MerchantModuleSettings from './merchant/MerchantModuleSettings';
import { buildMerchantPayload, emptyMerchantForm } from './merchantFormPayload';
import {
  Badge, Button, Card, Checkbox, Icons, PasswordField, SearchField, Select, Sheet, Table,
  TextArea, TextField, Toolbar,
} from '../../ui';

import { apiFetch } from '../../shared/api/httpClient';
import { apiUrl } from '../../shared/config';
import { readStoredUser } from '../../shared/useAuth';

const SEARCH_CATEGORIES = [
  { value: 'all', label: 'All Fields' },
  { value: 'account_number', label: 'Merchant Account' },
  { value: 'status', label: 'Status' },
  { value: 'account_type', label: 'Business Type' },
  { value: 'name', label: 'Name' },
];

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'ACTIVE' },
  { value: 'INACTIVE', label: 'INACTIVE' },
  { value: 'SUSPENDED', label: 'SUSPENDED' },
];

const ACCOUNT_TYPE_OPTIONS = [
  { value: 'personal', label: 'PERSONAL' },
  { value: 'business', label: 'BUSINESS' },
];

const ADMIN_ROLE_OPTIONS = [
  { value: 'Owner', label: 'Owner' },
  { value: 'Finance Administrator', label: 'Finance Administrator' },
  { value: 'Operations User', label: 'Operations User' },
  { value: 'Reporting User', label: 'Reporting User' },
  { value: 'Developer', label: 'Developer' },
];

const ADMIN_PRIVILEGE_GROUPS = [
  { label: 'Transactions', tone: 'blue', values: ['CREATE_BATCH_TX', 'APPROVE_BATCH_TX', 'ACCESS_TRANSACTION_LOG', 'DOWNLOAD_REPORTS'] },
  { label: 'Administration', tone: 'green', values: ['CREATE_ADMIN', 'UPDATE_ADMIN', 'DELETE_ADMIN', 'ACCESS_ADMIN'] },
  { label: 'Settings', tone: 'purple', values: ['ACCESS_SETTINGS', 'UPDATE_SETTINGS'] },
  { label: 'Reporting & Audit', tone: 'orange', values: ['ACCESS_AUDITTRAIL', 'ACCESS_SMS_LOG', 'SEND_SMS', 'BUY_SMS'] },
];

const MERCHANT_FORM_STEPS = [
  { key: 'details', number: 1, title: 'Merchant Details', copy: 'Business and contact information', icon: Icons.StoreIcon },
  { key: 'access', number: 2, title: 'API Access', copy: 'Available services', icon: Icons.LockIcon },
  { key: 'admins', number: 3, title: 'Merchant Administrators', copy: 'People and permissions', icon: Icons.UsersIcon },
];

function defaultAdmin() {
  return {
    name: '',
    email: '',
    phone: '',
    status: 'ACTIVE',
    role: 'Owner',
    privileges: common.merchant_privileges.map((privilege) => privilege.value),
    generate_pw: false,
    temporary_password: '',
    delete: false,
    id: '',
  };
}

function selectedPrivileges(admin) {
  return Array.isArray(admin?.privileges) ? admin.privileges : [];
}

function adminInitials(admin) {
  const source = admin?.name || admin?.email || 'New Admin';
  return source
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('') || 'NA';
}

function privilegeLabel(value) {
  return common.merchant_privileges.find((privilege) => privilege.value === value)?.text || value;
}

function statusTone(status) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'SUSPENDED') return 'warning';
  if (status === 'INACTIVE') return 'danger';
  return 'neutral';
}

function parseJson(text, fallbackMessage = 'The server did not return a readable response.') {
  const trimmed = (text || '').trim();
  if (!trimmed) {
    return { code: 'EMPTY_RESPONSE', message: fallbackMessage };
  }
  try {
    return JSON.parse(trimmed);
  } catch {
    return {
      code: 'INVALID_RESPONSE',
      message: 'The server returned an invalid response. Please retry the action.',
      raw: trimmed,
    };
  }
}

class ModuleMerchantsC extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      total: 0,
      pageSize: 50,
      allChecked: false,
      data: [],
      formdMode: 'new',
      formd: emptyMerchantForm(),
      title: '',
      formOpen: false,
      searchingValue: { value: '', category: 'all' },
      hasAccess: false,
      openMerchantAccount: {},
      statementDialogStateOpened: true,
      merchantSettingsOpen: false,
      selectedMerchantRow: null,
    };
  }

  componentDidMount() {
    if (this.isUserAllowedAccess()) {
      this.setState({ hasAccess: true }, () => this.getData());
    } else {
      this.messager.alert({ title: 'Access denied!', icon: 'info', msg: 'You are not allowed access to this section.' });
    }
  }

  isUserAllowedAccess() {
    const user = readStoredUser('admin');
    const allowed = new Set([
      'CREATE_MERCHANT', 'UPDATE_MERCHANT', 'ACTIVATE_MERCHANT',
      'CREATE_ADMIN', 'UPDATE_ADMIN', 'DELETE_ADMIN', 'ACCESS_ADMIN',
    ]);
    return Array.isArray(user.privileges) && user.privileges.some((item) => allowed.has(item.privilege));
  }

  getData() {
    this.props.loader('START');
    const searchData = { pageSize: this.state.pageSize, searchingValue: this.state.searchingValue, sort: 'asc' };
    apiFetch(apiUrl('/merchants/getMerchants'), {
      method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
      headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
      body: JSON.stringify(searchData),
    }).then((response) => response.text()).then((text) => {
      this.props.loader('STOP');
      const res = parseJson(text);
      if (res.code === '000') {
        const data = Array.isArray(res.data) ? res.data : [];
        this.setState({ data, total: res.total ?? data.length, allChecked: false });
        return;
      }
      if (res.code === '107') { this.sessionExpired(); return; }
      if (res.code === '110') { this.accessNotAllowed(res.message); return; }
      this.messager.alert({ title: 'Error ' + res.code, icon: 'error', msg: res.message });
    }).catch((error) => {
      this.props.loader('STOP');
      if (this.messager) this.messager.alert({ title: 'Error', icon: 'error', msg: error.message });
    });
  }

  accessNotAllowed(msg) {
    this.messager.alert({ title: 'Access denied!', icon: 'info', msg, result: () => this.setState({ hasAccess: false }) });
  }

  sessionExpired() {
    const { history } = this.props;
    this.messager.alert({ title: 'Session Expired!', icon: 'info', msg: 'Your session expired', result: () => history.push('/') });
  }

  addNew() {
    this.setState({ title: strings.add_merchant, formdMode: 'new', formOpen: true, formd: emptyMerchantForm() });
  }

  editRow(row) {
    this.setState({
      formdMode: 'edit',
      formOpen: true,
      formd: { ...emptyMerchantForm(), ...row, admins: row.admins || [], allowed_apis: row.allowed_apis || [] },
      title: 'Edit Merchant (' + row.name + ')',
    });
  }

  openAccount(row) {
    this.setState({ openMerchantAccount: row, statementDialogStateOpened: false, title: 'Merchant (' + row.name + ') - ' + row.account_number });
  }

  openSettings(row) {
    this.setState({ selectedMerchantRow: row, merchantSettingsOpen: true });
  }

  handleSearch(value) {
    this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, value } }), () => this.getData());
  }

  handleSearchCategory(category) {
    this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, category } }));
  }

  handleRowCheck(row, checked) {
    const data = this.state.data.map((r) => (r === row ? { ...r, selected: checked } : r));
    this.setState({ data, allChecked: data.length > 0 && data.every((r) => r.selected) });
  }

  handleAllCheck(checked) {
    this.setState({ allChecked: checked, data: this.state.data.map((row) => ({ ...row, selected: checked })) });
  }

  deleteRow(row) {
    this.messager.confirm({
      title: 'Delete Merchant', icon: 'info', msg: 'Are you sure you want to delete this merchant?',
      result: (ok) => {
        if (!ok) return;
        this.props.loader('START');
        apiFetch(apiUrl('/merchants/deleteMerchant'), {
          method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
          headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
          body: JSON.stringify(row),
        }).then((response) => response.text()).then((text) => {
          this.props.loader('STOP');
          const res = parseJson(text);
          if (res.code === '000') {
            this.messager.alert({ title: 'Success!', icon: 'info', msg: res.message, result: (r) => { if (r) this.getData(); } });
            return;
          }
          if (res.code === '107') { this.sessionExpired(); return; }
          this.messager.alert({ title: 'Error ' + (res.code || res.error || ''), icon: 'error', msg: res.message || res.error });
        }).catch((error) => {
          this.props.loader('STOP');
          this.messager.alert({ title: 'Error', icon: 'error', msg: error.message });
        });
      },
    });
  }

  saveRow(data) {
    const url = this.state.formdMode === 'edit'
      ? apiUrl('/merchants/editMerchant')
      : apiUrl('/merchants/addMerchant');
    this.props.loader('START');
    apiFetch(url, {
      method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
      headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
      body: JSON.stringify(buildMerchantPayload(data)),
    }).then((response) => response.text()).then((text) => {
      this.props.loader('STOP');
      const res = parseJson(text);
      if (res.code === '000') {
        this.messager.alert({
          title: 'Success!', icon: 'info', msg: res.message,
          result: (ok) => { if (ok) this.setState({ formOpen: false, formd: emptyMerchantForm() }, () => this.getData()); },
        });
        return;
      }
      if (res.code === '107') { this.sessionExpired(); return; }
      this.messager.alert({ title: 'Error ' + (res.code ? res.code : `${res.status} ${res.error}`), icon: 'error', msg: res.message || res.error });
    }).catch((error) => {
      this.props.loader('STOP');
      this.messager.alert({ title: 'Error', icon: 'error', msg: error.message });
    });
  }

  openOrCloseStatementDialog(state) {
    this.setState({ statementDialogStateOpened: state });
  }

  merchantSettingsSheet() {
    const merchant = this.state.selectedMerchantRow;
    return (
      <Sheet
        open={this.state.merchantSettingsOpen}
        onClose={() => this.setState({ merchantSettingsOpen: false })}
        title={merchant ? `Merchant Settings: ${merchant.name}` : 'Merchant Settings'}
        size="xl"
        footer={<Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ merchantSettingsOpen: false })}>{strings.close}</Button>}
      >
        {merchant ? (
          <MerchantModuleSettings
            key={merchant.id}
            sessionExpired={() => this.sessionExpired()}
            loader={this.props.loader}
            merchant_id={merchant.id}
          />
        ) : null}
      </Sheet>
    );
  }

  render() {
    if (!this.state.hasAccess) {
      return <div><Messager ref={ref => this.messager = ref}></Messager></div>;
    }

    const { searchingValue, data, allChecked } = this.state;
    const columns = [
      {
        key: 'ck', width: 44,
        header: <Checkbox checked={allChecked} onCheckedChange={(checked) => this.handleAllCheck(checked)} />,
        render: (row) => <Checkbox checked={Boolean(row.selected)} onCheckedChange={(checked) => this.handleRowCheck(row, checked)} />,
      },
      { key: 'account_number', header: 'Account', accessor: (row) => row.account_number, sortable: true, sortValue: (row) => row.account_number || '' },
      { key: 'name', header: 'Name', accessor: (row) => row.name, sortable: true, sortValue: (row) => row.name || '' },
      { key: 'short_name', header: 'Short Name', accessor: (row) => row.short_name },
      { key: 'account_type', header: 'Type', accessor: (row) => row.account_type },
      { key: 'created_by', header: 'Created By', accessor: (row) => row.created_by },
      { key: 'status', header: 'Status', render: (row) => <Badge tone={statusTone(row.status)}>{row.status}</Badge>, sortable: true, sortValue: (row) => row.status || '' },
      {
        key: 'actions', header: 'Actions', align: 'center',
        render: (row) => (
          <span className="ios-cell-actions">
            <Button variant="ghost" className="ios-btn--sm" onClick={() => this.openAccount(row)}>{strings.account}</Button>
            <Button variant="ghost" className="ios-btn--sm" onClick={() => this.openSettings(row)}>Settings</Button>
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
              <Icons.PlusIcon size={16} />{strings.add_merchant}
            </Button>
            <Toolbar.Spacer />
            <div style={{ minWidth: 150 }}>
              <Select id="merchant-category" value={searchingValue.category} options={SEARCH_CATEGORIES} onValueChange={(v) => this.handleSearchCategory(v)} />
            </div>
            <SearchField
              value={searchingValue.value}
              onValueChange={(v) => this.setState((prev) => ({ searchingValue: { ...prev.searchingValue, value: v } }))}
              onSubmit={(v) => this.handleSearch(v)}
              placeholder={strings.search_merchant}
            />
            <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ searchingValue: { value: '', category: 'all' } }, () => this.getData())}>{strings.clear}</Button>
          </Toolbar>
        </div>
        <Table
          columns={columns}
          rows={data}
          rowKey={(row, i) => row.id || row.account_number || i}
          pageSize={this.state.pageSize}
          isRowSelected={(row) => Boolean(row.selected)}
          emptyText="No merchants to display."
        />
        <MerchantFormDialog
          open={this.state.formOpen}
          formd={this.state.formd}
          title={this.state.title}
          onClose={() => this.setState({ formOpen: false })}
          saveRow={(payload) => this.saveRow(payload)}
        />
        <ModuleMerchantsAccount
          openOrCloseStatementDialog={(state) => this.openOrCloseStatementDialog(state)}
          title={this.state.title}
          loader={this.props.loader}
          sessionExpired={() => this.sessionExpired()}
          accessNotAllowed={(msg) => this.accessNotAllowed(msg)}
          messager={this.messager}
          statementDialogStateOpened={this.state.statementDialogStateOpened}
          openMerchantAccount={this.state.openMerchantAccount}
        />
        {this.merchantSettingsSheet()}
        <Messager ref={ref => this.messager = ref}></Messager>
      </Card>
    );
  }
}

class MerchantFormDialog extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      formd: props.formd,
      errors: {},
      selectedStep: 'details',
      activeAdminIndex: null,
    };
  }

  componentDidUpdate(prevProps) {
    if (prevProps.formd !== this.props.formd) {
      this.setState({
        formd: this.props.formd,
        errors: {},
        selectedStep: 'details',
        activeAdminIndex: null,
      });
    }
  }

  setField(name, value) {
    this.setState((prev) => ({ formd: { ...prev.formd, [name]: value } }));
  }

  setAllowedApi(value, checked) {
    this.setState((prev) => {
      const current = Array.isArray(prev.formd.allowed_apis) ? prev.formd.allowed_apis : [];
      const next = checked ? [...new Set([...current, value])] : current.filter((item) => item !== value);
      return { formd: { ...prev.formd, allowed_apis: next } };
    });
  }

  addAdmin() {
    this.setState((prev) => {
      const admins = [...(prev.formd.admins || []), defaultAdmin()];
      return {
        formd: { ...prev.formd, admins },
        selectedStep: 'admins',
        activeAdminIndex: admins.length - 1,
      };
    });
  }

  removeAdmin(index) {
    this.setState((prev) => {
      const admins = (prev.formd.admins || []).filter((admin, i) => i !== index);
      const nextIndex = admins.length === 0 ? null : Math.min(index, admins.length - 1);
      return { formd: { ...prev.formd, admins }, activeAdminIndex: nextIndex };
    });
  }

  updateAdmin(index, field, value) {
    this.setState((prev) => ({
      formd: {
        ...prev.formd,
        admins: (prev.formd.admins || []).map((admin, i) => (i === index ? { ...admin, [field]: value } : admin)),
      },
    }));
  }

  toggleAdminPrivilege(index, privilege, checked) {
    this.setState((prev) => ({
      formd: {
        ...prev.formd,
        admins: (prev.formd.admins || []).map((admin, i) => {
          if (i !== index) return admin;
          const current = selectedPrivileges(admin);
          const next = checked ? [...new Set([...current, privilege])] : current.filter((item) => item !== privilege);
          return { ...admin, privileges: next };
        }),
      },
    }));
  }

  toggleAdminPrivilegeGroup(index, privileges, checked) {
    this.setState((prev) => ({
      formd: {
        ...prev.formd,
        admins: (prev.formd.admins || []).map((admin, i) => {
          if (i !== index) return admin;
          const current = selectedPrivileges(admin);
          const next = checked
            ? [...new Set([...current, ...privileges])]
            : current.filter((item) => !privileges.includes(item));
          return { ...admin, privileges: next };
        }),
      },
    }));
  }

  selectAllAdminPrivileges(index, checked) {
    this.toggleAdminPrivilegeGroup(index, common.merchant_privileges.map((privilege) => privilege.value), checked);
  }

  validate() {
    const f = this.state.formd || {};
    const errors = {};
    if (!f.name) errors.name = 'Name is required';
    if (!f.short_name) errors.short_name = 'Short name is required';
    if (!f.status) errors.status = 'Status is required';
    if (!f.account_type) errors.account_type = 'Account type is required';
    const admins = (f.admins || []).filter((admin) => !admin.delete);
    for (let i = 0; i < admins.length; i += 1) {
      if (!admins[i].name || !admins[i].email || !admins[i].phone) {
        errors.admins = 'Every active admin needs name, email, and phone.';
        break;
      }
      if (!admins[i].id && !admins[i].generate_pw && !admins[i].temporary_password) {
        errors.admins = 'Set a temporary password for each new admin or choose generate temporary password.';
        break;
      }
    }
    this.setState({ errors, selectedStep: errors.admins ? 'admins' : this.state.selectedStep });
    return Object.keys(errors).length === 0;
  }

  saveRow() {
    if (!this.validate()) return;
    this.props.saveRow(buildMerchantPayload(this.state.formd));
  }

  close() {
    this.setState({
      formd: emptyMerchantForm(),
      errors: {},
      selectedStep: 'details',
      activeAdminIndex: null,
    }, () => this.props.onClose());
  }

  stepComplete(key, row) {
    if (key === 'details') return Boolean(row.name && row.short_name && row.status && row.account_type);
    if (key === 'access') return Array.isArray(row.allowed_apis) && row.allowed_apis.length > 0;
    if (key === 'admins') {
      const admins = (row.admins || []).filter((admin) => !admin.delete);
      return admins.length > 0 && admins.every((admin) => (
        admin.name && admin.email && admin.phone && (admin.id || admin.generate_pw || admin.temporary_password)
      ));
    }
    return false;
  }

  renderStepRail(row) {
    return (
      <aside className="cpay-merchant-wizard-rail" aria-label="Merchant setup steps">
        <div className="cpay-merchant-rail-steps">
          {MERCHANT_FORM_STEPS.map((step) => {
            const StepIcon = step.icon;
            const active = this.state.selectedStep === step.key;
            const complete = this.stepComplete(step.key, row);
            return (
              <button
                key={step.key}
                type="button"
                className={`cpay-merchant-step ${active ? 'cpay-merchant-step-active' : ''} ${complete ? 'cpay-merchant-step-complete' : ''}`.trim()}
                onClick={() => this.setState({ selectedStep: step.key })}
              >
                <span className="cpay-merchant-step-icon"><StepIcon size={17} /></span>
                <span>
                  <strong>{step.number}. {step.title}</strong>
                  <small>{step.copy}</small>
                </span>
                {complete ? <Icons.CheckIcon size={16} /> : <i aria-hidden="true" />}
              </button>
            );
          })}
        </div>
        <div className="cpay-merchant-help-card">
          <Icons.HeadsetIcon size={18} />
          <strong>Need help?</strong>
          <button type="button">View documentation</button>
          <button type="button">Contact support</button>
        </div>
      </aside>
    );
  }

  renderDetails(row, errors) {
    return (
      <section className="cpay-merchant-panel">
        <header className="cpay-merchant-panel-header">
          <span><Icons.StoreIcon size={18} /></span>
          <div>
            <h3>Merchant Details</h3>
            <p>Business profile, account type, and operating status.</p>
          </div>
        </header>
        <div className="cpay-merchant-form-grid">
          <TextField id="merchant-name" label="Name" value={row.name || ''} onValueChange={(value) => this.setField('name', value)} invalid={Boolean(errors.name)} />
          <TextField id="merchant-short-name" label="Short Name" value={row.short_name || ''} onValueChange={(value) => this.setField('short_name', value)} invalid={Boolean(errors.short_name)} />
          <Select id="merchant-status" label="Status" value={row.status || 'ACTIVE'} options={STATUS_OPTIONS} onValueChange={(value) => this.setField('status', value)} invalid={Boolean(errors.status)} />
          <Select id="merchant-account-type" label="Account Type" value={row.account_type || 'personal'} options={ACCOUNT_TYPE_OPTIONS} onValueChange={(value) => this.setField('account_type', value)} invalid={Boolean(errors.account_type)} />
          <div className="cpay-merchant-form-toggle">
            <Checkbox checked={Boolean(row.generate_new_keys)} onCheckedChange={(value) => this.setField('generate_new_keys', value)} label="Generate New Keys" />
            <p>Automatically generate API keys for this merchant.</p>
          </div>
        </div>
        {(row.private_key || row.public_key) ? (
          <div className="cpay-merchant-keys">
            <TextArea id="merchant-public-key" label="Public Key" rows={3} value={row.public_key || ''} onValueChange={(value) => this.setField('public_key', value)} />
            <TextArea id="merchant-private-key" label="Private Key" rows={3} value={row.private_key || ''} onValueChange={(value) => this.setField('private_key', value)} />
          </div>
        ) : null}
      </section>
    );
  }

  renderAccess(row) {
    const allowedApis = Array.isArray(row.allowed_apis) ? row.allowed_apis : [];
    return (
      <section className="cpay-merchant-panel">
        <header className="cpay-merchant-panel-header">
          <span><Icons.LockIcon size={18} /></span>
          <div>
            <h3>API Access</h3>
            <p>Enable products and platform services for this merchant.</p>
          </div>
        </header>
        <div className="cpay-api-access-grid">
          {common.allowed_apis.map((api) => (
            <div className={`cpay-api-access-option ${allowedApis.includes(api.value) ? 'cpay-api-access-option-active' : ''}`.trim()} key={api.value}>
              <Checkbox checked={allowedApis.includes(api.value)} onCheckedChange={(checked) => this.setAllowedApi(api.value, checked)} label={api.text} />
            </div>
          ))}
        </div>
      </section>
    );
  }

  renderAdmins(row, errors) {
    const admins = row.admins || [];
    return (
      <section className="cpay-merchant-panel cpay-merchant-panel-fill">
        <header className="cpay-merchant-panel-header cpay-merchant-panel-header-actions">
          <span><Icons.UsersIcon size={18} /></span>
          <div>
            <h3>Merchant Administrators</h3>
            <p>Manage people who can access and administer this merchant account.</p>
          </div>
          <Button variant="primary" className="ios-btn--sm" onClick={() => this.addAdmin()}>
            <Icons.PlusIcon size={15} />{strings.add_admin || 'Add Admin'}
          </Button>
        </header>
        {errors.admins ? <p className="cpay-form-error">{errors.admins}</p> : null}
        {admins.length ? (
          <div className="cpay-merchant-admin-list" role="list">
            {admins.map((admin, index) => {
              const active = this.state.activeAdminIndex === index;
              return (
                <div key={admin.id || admin.email || index} className={`cpay-merchant-admin-row ${active ? 'cpay-merchant-admin-row-active' : ''}`.trim()} role="listitem">
                  <button type="button" className="cpay-merchant-admin-main" onClick={() => this.setState({ activeAdminIndex: index, selectedStep: 'admins' })}>
                    <span className="cpay-merchant-admin-avatar">{adminInitials(admin)}</span>
                    <span>
                      <strong>{admin.name || 'New administrator'}</strong>
                      <small>{admin.email || 'Email not set'}</small>
                    </span>
                  </button>
                  <Badge tone={statusTone(admin.status || 'ACTIVE')}>{admin.status || 'ACTIVE'}</Badge>
                  <span className="cpay-merchant-admin-role">{admin.role || 'Finance Administrator'}</span>
                  <span className="cpay-merchant-admin-count">{selectedPrivileges(admin).length} permissions</span>
                  <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ activeAdminIndex: index, selectedStep: 'admins' })}>Edit</Button>
                  <Button variant="danger" className="ios-btn--sm" onClick={() => this.removeAdmin(index)}>Remove</Button>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="cpay-merchant-empty-state">
            <span><Icons.UsersIcon size={24} /></span>
            <h4>No administrators added yet</h4>
            <p>Add administrators to allow secure access to this merchant.</p>
            <Button variant="ghost" className="ios-btn--sm" onClick={() => this.addAdmin()}>
              <Icons.PlusIcon size={15} />Add First Admin
            </Button>
          </div>
        )}
      </section>
    );
  }

  renderAdminPermissionGroup(admin, index, group) {
    const selected = selectedPrivileges(admin);
    const selectedCount = group.values.filter((value) => selected.includes(value)).length;
    return (
      <div className={`cpay-permission-group cpay-permission-group-${group.tone}`} key={group.label}>
        <div className="cpay-permission-group-header">
          <strong>{group.label}</strong>
          <Checkbox
            checked={selectedCount === group.values.length}
            onCheckedChange={(checked) => this.toggleAdminPrivilegeGroup(index, group.values, checked)}
            label={`${selectedCount} of ${group.values.length} selected`}
          />
        </div>
        <div className="cpay-permission-grid">
          {group.values.map((privilege) => (
            <Checkbox
              key={privilege}
              checked={selected.includes(privilege)}
              onCheckedChange={(checked) => this.toggleAdminPrivilege(index, privilege, checked)}
              label={privilegeLabel(privilege)}
            />
          ))}
        </div>
      </div>
    );
  }

  renderAdminEditor(row) {
    const admins = row.admins || [];
    const index = this.state.activeAdminIndex ?? (admins.length ? 0 : null);
    const admin = index == null ? null : admins[index];
    if (!admin) {
      return (
        <aside className="cpay-merchant-side-panel">
          <header className="cpay-merchant-side-header">
            <h3>Add Administrator</h3>
            <p>Create or select an administrator to configure access.</p>
          </header>
          <div className="cpay-merchant-side-empty">
            <Icons.UsersIcon size={24} />
            <Button variant="primary" className="ios-btn--sm" onClick={() => this.addAdmin()}>
              <Icons.PlusIcon size={15} />Add Admin
            </Button>
          </div>
        </aside>
      );
    }

    const selected = selectedPrivileges(admin);
    const allPrivilegesSelected = common.merchant_privileges.every((privilege) => selected.includes(privilege.value));
    return (
      <aside className="cpay-merchant-side-panel">
        <header className="cpay-merchant-side-header">
          <div>
            <h3>{admin.name ? 'Edit Administrator' : 'Add Administrator'}</h3>
            <p>Account details and merchant permissions.</p>
          </div>
          <button type="button" className="cpay-merchant-side-close" onClick={() => this.setState({ activeAdminIndex: null })} aria-label="Clear selected administrator">
            <Icons.CloseIcon size={15} />
          </button>
        </header>
        <div className="cpay-merchant-side-scroll">
          <div className="cpay-merchant-side-section">
            <h4>Administrator Information</h4>
            <TextField id={`merchant-admin-name-${index}`} label="Full Name" value={admin.name || ''} onValueChange={(value) => this.updateAdmin(index, 'name', value)} />
            <TextField id={`merchant-admin-email-${index}`} label="Email Address" type="email" value={admin.email || ''} onValueChange={(value) => this.updateAdmin(index, 'email', value)} />
            <TextField id={`merchant-admin-phone-${index}`} label="Phone Number" value={admin.phone || ''} onValueChange={(value) => this.updateAdmin(index, 'phone', value)} />
          </div>
          <div className="cpay-merchant-side-section">
            <h4>Account Settings</h4>
            <div className="cpay-merchant-side-grid">
              <Select id={`merchant-admin-status-${index}`} label="Status" value={admin.status || 'ACTIVE'} options={STATUS_OPTIONS} onValueChange={(value) => this.updateAdmin(index, 'status', value)} />
              <Select id={`merchant-admin-role-${index}`} label="Role" value={admin.role || 'Finance Administrator'} options={ADMIN_ROLE_OPTIONS} onValueChange={(value) => this.updateAdmin(index, 'role', value)} />
            </div>
          </div>
          <div className="cpay-merchant-side-section">
            <div className="cpay-merchant-permission-title">
              <h4>Permissions</h4>
              <Checkbox
                checked={allPrivilegesSelected}
                onCheckedChange={(checked) => this.selectAllAdminPrivileges(index, checked)}
                label="Select all"
              />
            </div>
            {ADMIN_PRIVILEGE_GROUPS.map((group) => this.renderAdminPermissionGroup(admin, index, group))}
          </div>
          <div className="cpay-merchant-side-section">
            <h4>Password Options</h4>
            <PasswordField
              id={`merchant-admin-temporary-password-${index}`}
              label={admin.id ? 'New temporary password' : 'Temporary password'}
              value={admin.temporary_password || ''}
              onValueChange={(value) => this.updateAdmin(index, 'temporary_password', value)}
              autoComplete="new-password"
              placeholder={admin.id ? 'Leave blank to keep current password' : 'Set first sign-in password'}
            />
            <p className="cpay-merchant-side-hint">
              Use this password for first login. If you generate one instead, make sure email delivery is configured.
            </p>
            <Checkbox checked={Boolean(admin.generate_pw)} onCheckedChange={(value) => this.updateAdmin(index, 'generate_pw', value)} label="Generate temporary password" />
            <Checkbox checked={Boolean(admin.delete)} onCheckedChange={(value) => this.updateAdmin(index, 'delete', value)} label="Mark administrator for deletion" />
          </div>
        </div>
      </aside>
    );
  }

  renderSummary(row) {
    const allowedApis = Array.isArray(row.allowed_apis) ? row.allowed_apis : [];
    const admins = row.admins || [];
    return (
      <aside className="cpay-merchant-side-panel">
        <header className="cpay-merchant-side-header">
          <h3>Setup Summary</h3>
          <p>Review the merchant profile before saving.</p>
        </header>
        <div className="cpay-merchant-summary">
          <div>
            <span>Merchant</span>
            <strong>{row.name || 'Not set'}</strong>
          </div>
          <div>
            <span>Short name</span>
            <strong>{row.short_name || 'Not set'}</strong>
          </div>
          <div>
            <span>Status</span>
            <Badge tone={statusTone(row.status || 'ACTIVE')}>{row.status || 'ACTIVE'}</Badge>
          </div>
          <div>
            <span>API services</span>
            <strong>{allowedApis.length}</strong>
          </div>
          <div>
            <span>Administrators</span>
            <strong>{admins.length}</strong>
          </div>
        </div>
        <div className="cpay-merchant-summary-actions">
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ selectedStep: 'access' })}>Review APIs</Button>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ selectedStep: 'admins' })}>Review admins</Button>
        </div>
      </aside>
    );
  }

  renderActiveStep(row, errors) {
    if (this.state.selectedStep === 'access') return this.renderAccess(row);
    if (this.state.selectedStep === 'admins') return this.renderAdmins(row, errors);
    return this.renderDetails(row, errors);
  }

  renderSidePanel(row) {
    if (this.state.selectedStep === 'admins') return this.renderAdminEditor(row);
    return this.renderSummary(row);
  }

  goToPreviousStep() {
    const currentIndex = MERCHANT_FORM_STEPS.findIndex((step) => step.key === this.state.selectedStep);
    if (currentIndex <= 0) return;
    this.setState({ selectedStep: MERCHANT_FORM_STEPS[currentIndex - 1].key });
  }

  goToNextStep() {
    const currentIndex = MERCHANT_FORM_STEPS.findIndex((step) => step.key === this.state.selectedStep);
    if (currentIndex < 0 || currentIndex >= MERCHANT_FORM_STEPS.length - 1) return;
    this.setState({ selectedStep: MERCHANT_FORM_STEPS[currentIndex + 1].key });
  }

  render() {
    const row = this.state.formd || emptyMerchantForm();
    const errors = this.state.errors;
    const currentIndex = MERCHANT_FORM_STEPS.findIndex((step) => step.key === this.state.selectedStep);
    const hasPrevious = currentIndex > 0;
    const hasNext = currentIndex < MERCHANT_FORM_STEPS.length - 1;
    const isEdit = String(this.props.title || '').startsWith('Edit');
    const title = (
      <span className="cpay-merchant-sheet-title">
        <strong>{this.props.title}</strong>
        <small>{isEdit ? 'Update account details, services, and access.' : 'Create a new merchant account and configure access.'}</small>
      </span>
    );

    return (
      <Sheet
        open={this.props.open}
        onClose={() => this.close()}
        title={title}
        size="xl"
        className="cpay-merchant-sheet"
        footer={<>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.close()}>Cancel</Button>
          <div className="cpay-merchant-footer-spacer" />
          <Button variant="ghost" className="ios-btn--sm" disabled={!hasPrevious} onClick={() => this.goToPreviousStep()}>Previous</Button>
          <Button variant="ghost" className="ios-btn--sm" disabled={!hasNext} onClick={() => this.goToNextStep()}>Next</Button>
          <Button variant="primary" className="ios-btn--sm" onClick={() => this.saveRow()}>
            <Icons.CheckIcon size={15} />Save Merchant
          </Button>
        </>}
      >
        <div className="cpay-merchant-wizard">
          {this.renderStepRail(row)}
          <main className="cpay-merchant-wizard-main">
            {Object.values(errors).length ? <p className="cpay-form-error">{Object.values(errors)[0]}</p> : null}
            {this.renderActiveStep(row, errors)}
          </main>
          {this.renderSidePanel(row)}
        </div>
      </Sheet>
    );
  }
}

const ModuleMerchants = withRouter(ModuleMerchantsC);

export default ModuleMerchants;
