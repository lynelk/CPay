import React from 'react';
import Messager from '../../StableMessager';
import { withRouter } from '../../../shared/router/compat';
import {
  Card, Toolbar, Table, Select, Badge, Sheet, Button, TextField, PasswordField, Icons,
} from '../../../ui';
import { apiFetch } from '../../../shared/api/httpClient';
import { apiUrl } from '../../../shared/config';

const ROLE_OPTIONS = [
  { value: 'OWNER', label: 'Owner · full account authority' },
  { value: 'FINANCE', label: 'Finance · payments, statements and billing' },
  { value: 'DEVELOPER', label: 'Developer · communication and integrations' },
  { value: 'VIEWER', label: 'Viewer · read-only payments and statements' },
];

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'ACTIVE' },
  { value: 'INACTIVE', label: 'INACTIVE' },
  { value: 'SUSPENDED', label: 'SUSPENDED' },
];

const EMPTY_FORM = {
  id: null,
  name: '',
  email: '',
  phone: '',
  password: '',
  status: 'ACTIVE',
  role: 'VIEWER',
};

function statusTone(status) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'SUSPENDED') return 'danger';
  return 'neutral';
}

class MerchantModuleAdminsC extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      data: [],
      loading: false,
      dialogOpen: false,
      mode: 'new',
      form: { ...EMPTY_FORM },
      error: '',
    };
  }

  componentDidMount() {
    this.loadTeam();
  }

  async request(path, options = {}) {
    const response = await apiFetch(apiUrl(path), {
      mode: 'cors',
      cache: 'no-cache',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      redirect: 'follow',
      referrer: 'no-referrer',
      ...options,
    });
    let body = {};
    try { body = await response.json(); } catch { body = {}; }
    if (response.status === 401) {
      this.props.sessionExpired?.();
      throw new Error('Your CPay session has expired.');
    }
    if (!response.ok || (body.code && body.code !== '000')) {
      throw new Error(body.message || 'Merchant team request failed.');
    }
    return body;
  }

  async loadTeam() {
    this.props.loader?.('START');
    try {
      const result = await this.request('/api/v2/merchant-self-service/team', { method: 'GET' });
      this.setState({ data: Array.isArray(result.data) ? result.data : [], error: '' });
    } catch (error) {
      this.setState({ error: error.message });
      this.messager?.alert({ title: 'Team access', icon: 'error', msg: error.message });
    } finally {
      this.props.loader?.('STOP');
    }
  }

  openNew() {
    this.setState({ dialogOpen: true, mode: 'new', form: { ...EMPTY_FORM }, error: '' });
  }

  openEdit(row) {
    this.setState({
      dialogOpen: true,
      mode: 'edit',
      form: {
        id: row.id,
        name: row.name || '',
        email: row.email || '',
        phone: row.phone || '',
        password: '',
        status: row.status || 'ACTIVE',
        role: row.role || 'VIEWER',
      },
      error: '',
    });
  }

  change(name, value) {
    this.setState((state) => ({ form: { ...state.form, [name]: value } }));
  }

  async save() {
    const { form, mode } = this.state;
    if (!form.name || !form.email || !form.phone || (mode === 'new' && !form.password)) {
      this.setState({ error: 'Name, email and phone are required. New users also require a password.' });
      return;
    }
    this.props.loader?.('START');
    try {
      const path = mode === 'new'
        ? '/api/v2/merchant-self-service/team'
        : `/api/v2/merchant-self-service/team/${form.id}`;
      await this.request(path, {
        method: mode === 'new' ? 'POST' : 'PUT',
        body: JSON.stringify(form),
      });
      this.setState({ dialogOpen: false, form: { ...EMPTY_FORM }, error: '' });
      await this.loadTeam();
    } catch (error) {
      this.setState({ error: error.message });
    } finally {
      this.props.loader?.('STOP');
    }
  }

  deleteUser(row) {
    this.messager?.confirm({
      title: 'Delete team member',
      icon: 'info',
      msg: `Delete ${row.name || row.email}? The last active OWNER cannot be removed.`,
      result: async (confirmed) => {
        if (!confirmed) return;
        this.props.loader?.('START');
        try {
          await this.request(`/api/v2/merchant-self-service/team/${row.id}`, { method: 'DELETE' });
          await this.loadTeam();
        } catch (error) {
          this.messager?.alert({ title: 'Unable to delete user', icon: 'error', msg: error.message });
        } finally {
          this.props.loader?.('STOP');
        }
      },
    });
  }

  renderDialog() {
    const { dialogOpen, mode, form, error } = this.state;
    return (
      <Sheet
        open={dialogOpen}
        onClose={() => this.setState({ dialogOpen: false, error: '' })}
        title={mode === 'new' ? 'Add team member' : `Edit ${form.name || 'team member'}`}
        size="md"
        footer={<>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ dialogOpen: false, error: '' })}>Close</Button>
          <Button variant="primary" className="ios-btn--sm" onClick={() => this.save()}>Save</Button>
        </>}
      >
        <div className="ios-form">
          {error ? <div className="ios-alert ios-alert--error">{error}</div> : null}
          <TextField id="team-name" label="Name" value={form.name} onValueChange={(v) => this.change('name', v)} />
          <TextField id="team-email" label="Email" type="email" value={form.email} onValueChange={(v) => this.change('email', v)} />
          <TextField id="team-phone" label="Phone" value={form.phone} onValueChange={(v) => this.change('phone', v)} />
          <PasswordField
            id="team-password"
            label={mode === 'new' ? 'Temporary password' : 'New password (leave blank to keep current)'}
            value={form.password}
            onValueChange={(v) => this.change('password', v)}
          />
          <Select id="team-role" label="Role" value={form.role} options={ROLE_OPTIONS} onValueChange={(v) => this.change('role', v)} />
          <Select id="team-status" label="Status" value={form.status} options={STATUS_OPTIONS} onValueChange={(v) => this.change('status', v)} />
        </div>
      </Sheet>
    );
  }

  render() {
    const columns = [
      { key: 'name', header: 'Name', accessor: (row) => row.name },
      { key: 'email', header: 'Email', accessor: (row) => row.email },
      { key: 'phone', header: 'Phone', accessor: (row) => row.phone },
      { key: 'role', header: 'Role', render: (row) => <Badge tone={row.role === 'OWNER' ? 'success' : 'neutral'}>{row.role || 'VIEWER'}</Badge> },
      { key: 'status', header: 'Status', render: (row) => <Badge tone={statusTone(row.status)}>{row.status}</Badge> },
      {
        key: 'actions',
        header: 'Actions',
        align: 'center',
        render: (row) => <span className="ios-cell-actions">
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.openEdit(row)}>Edit</Button>
          <Button variant="danger" className="ios-btn--sm" onClick={() => this.deleteUser(row)}>Delete</Button>
        </span>,
      },
    ];

    return (
      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <Button variant="primary" className="ios-btn--sm" onClick={() => this.openNew()}>
              <Icons.PlusIcon size={16} /> Add team member
            </Button>
            <Toolbar.Spacer />
            <span>Access is assigned by MerchantRole and applies across the whole portal after one sign-in.</span>
          </Toolbar>
        </div>
        <Table
          columns={columns}
          rows={this.state.data}
          rowKey={(row) => row.id}
          pageSize={50}
          emptyText="No merchant team members to display."
        />
        <Messager ref={(ref) => { this.messager = ref; }} />
        {this.renderDialog()}
      </Card>
    );
  }
}

export default withRouter(MerchantModuleAdminsC);
