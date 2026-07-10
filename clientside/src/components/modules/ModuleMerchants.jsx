import React from 'react';
import Messager from '../StableMessager';
import { withRouter } from '../../shared/router/compat';
import common from '../Common';
import strings from '../locale';
import ModuleMerchantsAccount from './ModuleMerchantsAccount';
import MerchantModuleSettings from './merchant/MerchantModuleSettings';
import { buildMerchantPayload, emptyMerchantForm } from './merchantFormPayload';
import {
  Badge, Button, Card, Checkbox, Icons, SearchField, Select, Sheet, Table,
  TextArea, TextField, Toolbar,
} from '../../ui';

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

function statusTone(status) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'SUSPENDED') return 'warning';
  if (status === 'INACTIVE') return 'danger';
  return 'neutral';
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(error instanceof Error ? error.message : 'Invalid server response');
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
    const user = localStorage.getItem('user') != null ? JSON.parse(localStorage.getItem('user')) : {};
    const allowed = new Set([
      'CREATE_MERCHANT', 'UPDATE_MERCHANT', 'ACTIVATE_MERCHANT',
      'CREATE_ADMIN', 'UPDATE_ADMIN', 'DELETE_ADMIN', 'ACCESS_ADMIN',
    ]);
    return Array.isArray(user.privileges) && user.privileges.some((item) => allowed.has(item.privilege));
  }

  getData() {
    this.props.loader('START');
    const searchData = { pageSize: this.state.pageSize, searchingValue: this.state.searchingValue, sort: 'asc' };
    fetch(common.base_url + '/merchants/getMerchants', {
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
        fetch(common.base_url + '/merchants/deleteMerchant', {
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
      ? common.base_url + '/merchants/editMerchant'
      : common.base_url + '/merchants/addMerchant';
    this.props.loader('START');
    fetch(url, {
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
    this.state = { formd: props.formd, errors: {} };
  }

  componentDidUpdate(prevProps) {
    if (prevProps.formd !== this.props.formd) {
      this.setState({ formd: this.props.formd, errors: {} });
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
    this.setState((prev) => ({
      formd: {
        ...prev.formd,
        admins: [...(prev.formd.admins || []), { name: '', email: '', phone: '', status: 'ACTIVE', privileges: [], generate_pw: false, delete: false, id: '' }],
      },
    }));
  }

  removeLastAdmin() {
    this.setState((prev) => ({ formd: { ...prev.formd, admins: (prev.formd.admins || []).slice(0, -1) } }));
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
          const current = Array.isArray(admin.privileges) ? admin.privileges : [];
          const next = checked ? [...new Set([...current, privilege])] : current.filter((item) => item !== privilege);
          return { ...admin, privileges: next };
        }),
      },
    }));
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
    }
    this.setState({ errors });
    return Object.keys(errors).length === 0;
  }

  saveRow() {
    if (!this.validate()) return;
    this.props.saveRow(buildMerchantPayload(this.state.formd));
  }

  close() {
    this.setState({ formd: emptyMerchantForm(), errors: {} }, () => this.props.onClose());
  }

  renderPrivilegeSelector(admin, index) {
    const selected = Array.isArray(admin.privileges) ? admin.privileges : [];
    return (
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
        {common.merchant_privileges.map((privilege) => (
          <Checkbox
            key={privilege.value}
            checked={selected.includes(privilege.value)}
            onCheckedChange={(checked) => this.toggleAdminPrivilege(index, privilege.value, checked)}
            label={privilege.text}
          />
        ))}
      </div>
    );
  }

  render() {
    const row = this.state.formd || emptyMerchantForm();
    const errors = this.state.errors;
    const allowedApis = Array.isArray(row.allowed_apis) ? row.allowed_apis : [];
    const admins = row.admins || [];
    const adminColumns = [
      { key: 'rn', header: '#', width: 40, render: (admin, index) => index + 1 },
      { key: 'name', header: 'Name', render: (admin, index) => <TextField id={`merchant-admin-name-${index}`} label="" value={admin.name || ''} onValueChange={(value) => this.updateAdmin(index, 'name', value)} /> },
      { key: 'email', header: 'Email', render: (admin, index) => <TextField id={`merchant-admin-email-${index}`} label="" value={admin.email || ''} onValueChange={(value) => this.updateAdmin(index, 'email', value)} /> },
      { key: 'phone', header: 'Phone', render: (admin, index) => <TextField id={`merchant-admin-phone-${index}`} label="" value={admin.phone || ''} onValueChange={(value) => this.updateAdmin(index, 'phone', value)} /> },
      { key: 'status', header: 'Status', render: (admin, index) => <Select id={`merchant-admin-status-${index}`} value={admin.status || 'ACTIVE'} options={STATUS_OPTIONS} onValueChange={(value) => this.updateAdmin(index, 'status', value)} /> },
      { key: 'privileges', header: 'Privileges', render: (admin, index) => this.renderPrivilegeSelector(admin, index) },
      { key: 'generate_pw', header: 'Generate PW', align: 'center', render: (admin, index) => <Checkbox checked={Boolean(admin.generate_pw)} onCheckedChange={(value) => this.updateAdmin(index, 'generate_pw', value)} /> },
      { key: 'delete', header: strings.delete, align: 'center', render: (admin, index) => <Checkbox checked={Boolean(admin.delete)} onCheckedChange={(value) => this.updateAdmin(index, 'delete', value)} /> },
    ];

    return (
      <Sheet
        open={this.props.open}
        onClose={() => this.close()}
        title={this.props.title}
        size="xl"
        footer={<>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.close()}>{strings.close}</Button>
          <Button variant="primary" className="ios-btn--sm" onClick={() => this.saveRow()}>{strings.save}</Button>
        </>}
      >
        <div className="ios-form" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 'var(--ios-space-4)' }}>
          <TextField id="merchant-name" label="Name" value={row.name || ''} onValueChange={(value) => this.setField('name', value)} invalid={Boolean(errors.name)} />
          <TextField id="merchant-short-name" label="Short Name" value={row.short_name || ''} onValueChange={(value) => this.setField('short_name', value)} invalid={Boolean(errors.short_name)} />
          <Select id="merchant-status" label="Status" value={row.status || 'ACTIVE'} options={STATUS_OPTIONS} onValueChange={(value) => this.setField('status', value)} invalid={Boolean(errors.status)} />
          <Select id="merchant-account-type" label="Account Type" value={row.account_type || 'personal'} options={ACCOUNT_TYPE_OPTIONS} onValueChange={(value) => this.setField('account_type', value)} invalid={Boolean(errors.account_type)} />
          <Checkbox checked={Boolean(row.generate_new_keys)} onCheckedChange={(value) => this.setField('generate_new_keys', value)} label="Generate New Keys" />
        </div>
        {Object.values(errors).length ? <p style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{Object.values(errors)[0]}</p> : null}

        <h3 className="ios-section-title" style={{ marginTop: 'var(--ios-space-5)' }}>Allowed APIs Access</h3>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', marginBottom: 'var(--ios-space-4)' }}>
          {common.allowed_apis.map((api) => (
            <Checkbox key={api.value} checked={allowedApis.includes(api.value)} onCheckedChange={(checked) => this.setAllowedApi(api.value, checked)} label={api.text} />
          ))}
        </div>

        {(row.private_key || row.public_key) ? (
          <div className="ios-form" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 'var(--ios-space-4)' }}>
            <TextArea id="merchant-public-key" label="Public Key" rows={3} value={row.public_key || ''} onValueChange={(value) => this.setField('public_key', value)} />
            <TextArea id="merchant-private-key" label="Private Key" rows={3} value={row.private_key || ''} onValueChange={(value) => this.setField('private_key', value)} />
          </div>
        ) : null}

        <h3 className="ios-section-title" style={{ marginTop: 'var(--ios-space-5)' }}>Merchant Admins</h3>
        <Toolbar>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.addAdmin()}>{strings.add_admin || 'Add Admin'}</Button>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.removeLastAdmin()}>Remove Admin</Button>
        </Toolbar>
        {errors.admins ? <p style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{errors.admins}</p> : null}
        <div style={{ marginTop: 'var(--ios-space-3)' }}>
          <Table columns={adminColumns} rows={admins} rowKey={(admin, index) => admin.id || admin.email || index} pageSize={50} emptyText="No merchant admins yet." />
        </div>
      </Sheet>
    );
  }
}

const ModuleMerchants = withRouter(ModuleMerchantsC);

export default ModuleMerchants;
