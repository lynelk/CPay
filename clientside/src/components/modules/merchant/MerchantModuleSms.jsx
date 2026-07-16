import React from 'react';
import Messager from '../../StableMessager';
import { withRouter } from '../../../shared/router/compat';
import common from '../../Common';
import strings from '../../locale';
import { replaceSmsColumnToken } from './smsTemplate';
import ReactExport from '../../../shared/export/ExcelExport';
import {
  Badge, Button, Card, Checkbox, DateField, FileButton, Icons, SearchField,
  Select, Sheet, Table, TextArea, TextField, Toolbar,
} from '../../../ui';

const ExcelFile = ReactExport.ExcelFile;
const ExcelSheet = ReactExport.ExcelFile.ExcelSheet;
const ExcelColumn = ReactExport.ExcelFile.ExcelColumn;

const toOptions = (arr) => (Array.isArray(arr) ? arr.map((item) => ({ value: item.value, label: item.text })) : []);

const SEARCH_CATEGORIES = [
  { value: 'all', label: 'All Fields' },
  { value: 'recipients', label: 'Recipient' },
  { value: 'status', label: 'Status' },
  { value: 'content', label: 'Content' },
];

const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  { value: 'PENDING', label: 'PENDING' },
  { value: 'PROCESSING', label: 'PROCESSING' },
  { value: 'DONE', label: 'DONE' },
  { value: 'STOPPED', label: 'STOPPED' },
  { value: 'CANCELLED', label: 'CANCELLED' },
];

function defaultSearchRules() {
  return {
    start_date: common.formatDate(common.getDateMonthsBefore(new Date(), 6)),
    end_date: common.formatDate(new Date()),
    status: '',
    tx_type: '',
  };
}

function dateTimeToNative(value) {
  if (!value) return '';
  return String(value).replace(' ', 'T').slice(0, 16);
}

function nativeToDateTime(value) {
  if (!value) return '';
  return `${value.replace('T', ' ')}:00`;
}

function statusTone(status) {
  if (status === 'DONE') return 'success';
  if (status === 'STOPPED' || status === 'CANCELLED') return 'danger';
  if (status === 'PROCESSING') return 'info';
  if (status === 'PENDING') return 'warning';
  return 'neutral';
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(error instanceof Error ? error.message : 'Invalid server response');
  }
}

class MerchantModuleSmsC extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      total: 0,
      pageSize: 50,
      allChecked: false,
      data: [],
      formdMode: 'new',
      formd: this.emptySmsForm(),
      title: '',
      formOpen: false,
      searchingValue: { value: '', category: 'all' },
      search_rules: defaultSearchRules(),
      searchOpen: false,
      hasAccess: false,
      tx_details_row: {},
      detailsOpen: false,
      record_tx_data: { tx_type: 'SMS PURCHASE', amount: '', description: 'SMS purchase', balance_type: 'sms_balance' },
      recordTxOpen: false,
      recordTxErrors: {},
      balance_type: toOptions(common.balance_type),
      current_balances: [],
      sms_balance: '',
    };
  }

  componentDidMount() {
    if (this.isUserAllowedAccess()) {
      this.setState({ hasAccess: true }, () => this.getData());
    } else {
      this.messager.alert({ title: 'Access denied!', icon: 'info', msg: 'You are not allowed access to this section.' });
    }
  }

  emptySmsForm() {
    return {
      recipients: [],
      id: '',
      send_time: common.getDefaultDateTime(),
      content: '',
      status: 'PENDING',
      ismultiple: false,
    };
  }

  isUserAllowedAccess() {
    const user = localStorage.getItem('merchantUser') != null ? JSON.parse(localStorage.getItem('merchantUser')) : {};
    const allowed = new Set(['CREATE_BATCH_TX', 'APPROVE_BATCH_TX', 'DOWNLOAD_REPORTS', 'ACCESS_SMS_LOG', 'SEND_SMS', 'BUY_SMS']);
    return Array.isArray(user.privileges) && user.privileges.some((item) => allowed.has(item.privilege));
  }

  getData() {
    this.props.loader('START');
    const searchData = {
      search_rules: this.state.search_rules,
      pageSize: this.state.pageSize,
      searchingValue: this.state.searchingValue,
      sort: 'asc',
    };
    fetch(common.base_url + '/transactions/getMerchantSms', {
      method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
      headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
      body: JSON.stringify(searchData),
    }).then((response) => response.text()).then((text) => {
      this.props.loader('STOP');
      const res = parseJson(text);
      if (res.code === '000') {
        const data = Array.isArray(res.data) ? res.data : [];
        this.setState({ data, total: res.total ?? data.length, allChecked: false, current_balances: res.balances || [] }, () => {
          this.generateBalanceTypesList(res.balances || []);
        });
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

  generateBalanceTypesList(balances) {
    const options = [];
    let smsBalance = '';
    for (let i = 0; i < balances.length; i += 1) {
      options.push({ value: balances[i].balance_type, label: `${balances[i].code} (${common.formatNumber(balances[i].amount)})` });
      if (balances[i].balance_type === 'sms_balance') {
        smsBalance = `${balances[i].code} ${common.formatNumber(balances[i].amount)}`;
      }
    }
    this.setState({ balance_type: options.length ? options : toOptions(common.balance_type), sms_balance: smsBalance });
  }

  accessNotAllowed(msg) {
    this.messager.alert({ title: 'Access denied!', icon: 'info', msg, result: () => this.setState({ hasAccess: false }) });
  }

  sessionExpired() {
    const { history } = this.props;
    this.messager.alert({ title: 'Session Expired!', icon: 'info', msg: 'Your session expired', result: () => history.push('/portal') });
  }

  addNew() {
    this.setState({ title: strings.new_sms, formdMode: 'new', formOpen: true, formd: this.emptySmsForm() });
  }

  editRow(row) {
    this.setState({ formdMode: 'edit', formOpen: true, formd: { ...row, recipients: row.recipients || [] }, title: 'Edit SMS' });
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

  saveRow(data) {
    const payload = {
      ...data,
      recipients: (data.recipients || []).map((recipient) => ({
        ...recipient,
        phone: recipient.phone || recipient.msisdn || '',
        content: recipient.content || data.content || '',
        delete: Boolean(recipient.delete),
      })),
    };
    this.props.loader('START');
    fetch(common.base_url + '/transactions/saveSms', {
      method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
      headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
      body: JSON.stringify(payload),
    }).then((response) => response.text()).then((text) => {
      this.props.loader('STOP');
      const res = parseJson(text);
      if (res.code === '000') {
        this.messager.alert({
          title: 'Success!', icon: 'info', msg: res.message,
          result: (ok) => { if (ok) this.setState({ formOpen: false, formd: this.emptySmsForm() }, () => this.getData()); },
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

  attemptToCancel() {
    const selected = this.state.data.filter((row) => row.selected);
    if (selected.length === 0) {
      this.messager.alert({ title: 'No SMS selected', icon: 'info', msg: 'Select at least one SMS batch to cancel.' });
      return;
    }
    this.messager.confirm({
      title: 'Cancel SMS', icon: 'info', msg: 'Are you sure you want to cancel selected SMS batch(es)?',
      result: (ok) => {
        if (!ok) return;
        this.props.loader('START');
        fetch(common.base_url + '/transactions/cancelSms', {
          method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
          headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
          body: JSON.stringify(selected),
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

  recordTransactionRequest() {
    this.props.loader('START');
    fetch(common.base_url + '/transactions/buySms', {
      method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
      headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
      body: JSON.stringify(this.state.record_tx_data),
    }).then((response) => response.text()).then((text) => {
      this.props.loader('STOP');
      const res = parseJson(text);
      if (res.code === '000') {
        this.resetRecordTxForm(() => {
          this.setState({ recordTxOpen: false });
          this.messager.alert({ title: 'Success!', icon: 'info', msg: res.message, result: (ok) => { if (ok) this.getData(); } });
        });
        return;
      }
      if (res.code === '107') { this.sessionExpired(); return; }
      this.messager.alert({ title: 'Error ' + (res.code || res.error || ''), icon: 'error', msg: res.message || res.error });
    }).catch((error) => {
      this.props.loader('STOP');
      this.messager.alert({ title: 'Error', icon: 'error', msg: error.message });
    });
  }

  resetRecordTxForm(whenDone) {
    this.setState({
      record_tx_data: { tx_type: 'SMS PURCHASE', amount: '', description: 'SMS purchase', balance_type: 'sms_balance' },
      recordTxErrors: {},
    }, whenDone);
  }

  recordTxSubmit() {
    const f = this.state.record_tx_data;
    const errors = {};
    if (!f.balance_type) errors.balance_type = 'Balance type is required';
    if (f.amount === '' || Number.isNaN(Number(f.amount))) errors.amount = 'Enter a valid amount';
    this.setState({ recordTxErrors: errors });
    if (Object.keys(errors).length === 0) this.recordTransactionRequest();
  }

  handleRecordFormChange(name, value) {
    this.setState((prev) => ({ record_tx_data: { ...prev.record_tx_data, [name]: value } }));
  }

  clearSearch() {
    this.setState({ search_rules: defaultSearchRules() });
  }

  handleSearchFormChange(name, value) {
    this.setState((prev) => ({ search_rules: { ...prev.search_rules, [name]: value } }));
  }

  detailRow(label, value) {
    return (
      <div className="cpay-detail-row">
        <span className="cpay-detail-label">{label}</span>
        <span className="cpay-detail-value">{value}</span>
      </div>
    );
  }

  detailsSheet() {
    const row = this.state.tx_details_row || {};
    const recipients = row.recipients || [];
    const columns = [
      { key: 'rn', header: '#', width: 44, render: (r, i) => i + 1 },
      { key: 'msisdn', header: 'Phone number', accessor: (r) => r.msisdn || r.phone },
      { key: 'content', header: 'Content', accessor: (r) => r.content },
    ];
    return (
      <Sheet
        open={this.state.detailsOpen}
        onClose={() => this.setState({ detailsOpen: false })}
        title="SMS Details"
        size="lg"
        footer={<Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ detailsOpen: false })}>{strings.close}</Button>}
      >
        {this.detailRow('Content', <span style={{ whiteSpace: 'pre-wrap' }}>{row.content || ''}</span>)}
        {this.detailRow('Recipients', row.total_recipients)}
        {this.detailRow('Status', <Badge tone={statusTone(row.status)}>{row.status}</Badge>)}
        {this.detailRow('Charge', `UGX ${row.charge || 0}`)}
        {this.detailRow('Total Amount', `UGX ${row.total_amount || 0}`)}
        {this.detailRow('Created On', row.created_on)}
        {this.detailRow('Send Time', row.send_time)}
        <h3 className="cpay-sheet-section-title">Recipients</h3>
        <Table columns={columns} rows={recipients} rowKey={(r, i) => r.id || r.msisdn || r.phone || i} emptyText="No recipients to display." />
      </Sheet>
    );
  }

  searchSheet() {
    const s = this.state.search_rules;
    return (
      <Sheet
        open={this.state.searchOpen}
        onClose={() => this.setState({ searchOpen: false })}
        title="Search SMS"
        size="sm"
        footer={<>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.clearSearch()}>{strings.clear}</Button>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ searchOpen: false })}>{strings.close}</Button>
          <Button variant="primary" className="ios-btn--sm" onClick={() => this.setState({ searchOpen: false }, () => this.getData())}>{strings.go}</Button>
        </>}
      >
        <div className="ios-form">
          <DateField id="sms-start" label="Start Date" kind="date" value={s.start_date || ''} onValueChange={(v) => this.handleSearchFormChange('start_date', v)} />
          <DateField id="sms-end" label="End Date" kind="date" value={s.end_date || ''} onValueChange={(v) => this.handleSearchFormChange('end_date', v)} />
          <Select id="sms-status-filter" label="Status" value={s.status || ''} options={STATUS_OPTIONS} onValueChange={(v) => this.handleSearchFormChange('status', v)} />
        </div>
      </Sheet>
    );
  }

  recordSmsTxSheet() {
    const { record_tx_data: f, recordTxErrors: e } = this.state;
    return (
      <Sheet
        open={this.state.recordTxOpen}
        onClose={() => this.resetRecordTxForm(() => this.setState({ recordTxOpen: false }))}
        title={strings.buy_sms}
        size="sm"
        footer={<>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.resetRecordTxForm(() => this.setState({ recordTxOpen: false }))}>{strings.close}</Button>
          <Button variant="primary" className="ios-btn--sm" onClick={() => this.recordTxSubmit()}>{strings.buy_now || 'Buy Now'}</Button>
        </>}
      >
        <div className="ios-form">
          <Select id="sms-buy-balance" label="Balance Type" value={f.balance_type} options={this.state.balance_type} onValueChange={(v) => this.handleRecordFormChange('balance_type', v)} invalid={Boolean(e.balance_type)} />
          {e.balance_type ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{e.balance_type}</span> : null}
          <TextField id="sms-buy-amount" label="Amount" value={f.amount} onValueChange={(v) => this.handleRecordFormChange('amount', v)} invalid={Boolean(e.amount)} />
          {e.amount ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{e.amount}</span> : null}
        </div>
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
      { key: 'created_on', header: 'Created On', accessor: (row) => row.created_on, sortable: true, sortValue: (row) => row.created_on || '' },
      { key: 'send_time', header: 'Send Time', accessor: (row) => row.send_time, sortable: true, sortValue: (row) => row.send_time || '' },
      { key: 'recipients_string', header: 'Recipients', accessor: (row) => row.recipients_string },
      { key: 'status', header: 'Status', render: (row) => <Badge tone={statusTone(row.status)}>{row.status}</Badge>, sortable: true, sortValue: (row) => row.status || '' },
      { key: 'content', header: 'Content', accessor: (row) => row.content },
      { key: 'charge', header: 'Charge', numeric: true, accessor: (row) => row.charge },
      { key: 'total_recipients', header: 'No.', numeric: true, accessor: (row) => row.total_recipients },
      {
        key: 'actions', header: 'Actions', align: 'center',
        render: (row) => (
          <span className="ios-cell-actions">
            <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ tx_details_row: row, detailsOpen: true })}>{strings.details}</Button>
          </span>
        ),
      },
    ];

    return (
      <Card flush>
        <div style={{ padding: 'var(--ios-space-4)' }}>
          <Toolbar>
            <Button variant="primary" className="ios-btn--sm" onClick={() => this.addNew()}>
              <Icons.SmsIcon size={16} />{strings.send_sms}
            </Button>
            <Button variant="ghost" className="ios-btn--sm" onClick={() => this.setState({ recordTxOpen: true })}>
              <Icons.PaymentsIcon size={16} />{strings.buy_sms}
            </Button>
            <Button variant="danger" className="ios-btn--sm" onClick={() => this.attemptToCancel()}>Cancel Selected</Button>
            {this.state.sms_balance ? <Badge tone="info">SMS Balance: {this.state.sms_balance}</Badge> : null}
            <Toolbar.Spacer />
            <div style={{ minWidth: 150 }}>
              <Select id="sms-category" value={searchingValue.category} options={SEARCH_CATEGORIES} onValueChange={(v) => this.handleSearchCategory(v)} />
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
          rowKey={(row, i) => row.id || row.batch_id || row.created_on || i}
          pageSize={this.state.pageSize}
          isRowSelected={(row) => Boolean(row.selected)}
          emptyText="No SMS batches to display."
        />
        <PaymentFormDialog
          open={this.state.formOpen}
          loader={this.props.loader}
          getMessager={() => this.messager}
          onClose={() => this.setState({ formOpen: false })}
          formd={this.state.formd}
          title={this.state.title}
          saveRow={(payload) => this.saveRow(payload)}
        />
        {this.searchSheet()}
        {this.recordSmsTxSheet()}
        {this.detailsSheet()}
        <Messager ref={ref => this.messager = ref}></Messager>
      </Card>
    );
  }
}

class PaymentFormDialog extends React.Component {
  constructor(props) {
    super(props);
    this.state = { formd: props.formd, errors: {}, character_count: (props.formd.content || '').length };
  }

  componentDidUpdate(prevProps) {
    if (prevProps.formd !== this.props.formd) {
      this.setState({ formd: this.props.formd, errors: {}, character_count: (this.props.formd.content || '').length });
    }
  }

  setField(name, value) {
    this.setState((prev) => ({
      formd: { ...prev.formd, [name]: value },
      character_count: name === 'content' ? value.length : prev.character_count,
    }));
  }

  updateRecipient(index, field, value) {
    this.setState((prev) => ({
      formd: {
        ...prev.formd,
        recipients: (prev.formd.recipients || []).map((recipient, i) => (i === index ? { ...recipient, [field]: value } : recipient)),
      },
    }));
  }

  addRecipient() {
    this.setState((prev) => ({
      formd: {
        ...prev.formd,
        recipients: [...(prev.formd.recipients || []), { phone: '', content: prev.formd.content || '', delete: false, id: '' }],
      },
    }));
  }

  removeLastRecipient() {
    this.setState((prev) => ({ formd: { ...prev.formd, recipients: (prev.formd.recipients || []).slice(0, -1) } }));
  }

  removeAllRecipients() {
    this.setState((prev) => ({ formd: { ...prev.formd, recipients: [] } }));
  }

  applyContentToRecipients() {
    this.setState((prev) => ({
      formd: {
        ...prev.formd,
        recipients: (prev.formd.recipients || []).map((recipient) => ({ ...recipient, content: prev.formd.content || '' })),
      },
    }));
  }

  uploadRecipients(files) {
    const form = new FormData();
    for (let i = 0; i < files.length; i += 1) form.append('file', files[i]);
    this.props.loader('START');
    fetch(common.base_url + '/transactions/uploadSmsRecipientsFile', { method: 'POST', mode: 'cors', credentials: 'include', body: form })
      .then((response) => response.text()).then((text) => {
        this.props.loader('STOP');
        const messager = this.props.getMessager();
        let res;
        try { res = JSON.parse(text); } catch { if (messager) messager.alert({ title: 'Error', icon: 'error', msg: 'Invalid upload response' }); return; }
        if (res.state === 'ERROR') {
          if (messager) messager.alert({ title: 'Error', icon: 'error', msg: res.message });
          return;
        }
        if (res.state === 'OK') {
          this.setState((prev) => {
            const content = prev.formd.content || '';
            const rows = (res.data || []).map((item) => {
              let rowContent = content;
              if (prev.formd.ismultiple) {
                rowContent = replaceSmsColumnToken(rowContent, 'COLB', item.cellB);
                rowContent = replaceSmsColumnToken(rowContent, 'COLC', item.cellC);
                rowContent = replaceSmsColumnToken(rowContent, 'COLD', item.cellD);
                rowContent = replaceSmsColumnToken(rowContent, 'COLE', item.cellE);
                rowContent = replaceSmsColumnToken(rowContent, 'COLF', item.cellF);
              }
              return { ...item, phone: String(item.phone || item.msisdn || ''), content: item.content || rowContent, delete: Boolean(item.delete), id: item.id || '' };
            });
            return { formd: { ...prev.formd, recipients: [...(prev.formd.recipients || []), ...rows] } };
          });
        }
      }).catch((error) => {
        this.props.loader('STOP');
        const messager = this.props.getMessager();
        if (messager) messager.alert({ title: 'Error', icon: 'error', msg: error.message });
      });
  }

  validate() {
    const errors = {};
    const f = this.state.formd || {};
    const activeRecipients = (f.recipients || []).filter((recipient) => !recipient.delete);
    if (!f.content) errors.content = 'SMS content is required';
    if (!f.send_time) errors.send_time = 'Send time is required';
    if (activeRecipients.length === 0) errors.recipients = 'Add at least one phone number';
    for (let i = 0; i < activeRecipients.length; i += 1) {
      if (!activeRecipients[i].phone && !activeRecipients[i].msisdn) errors.recipients = 'Every recipient needs a phone number';
      if (!activeRecipients[i].content) errors.recipients = 'Every recipient needs content';
    }
    this.setState({ errors });
    return Object.keys(errors).length === 0;
  }

  submit() {
    if (!this.validate()) return;
    this.props.saveRow(this.state.formd);
  }

  close() {
    this.setState({ formd: { recipients: [], content: '', send_time: common.getDefaultDateTime(), ismultiple: false }, errors: {}, character_count: 0 }, () => this.props.onClose());
  }

  render() {
    const row = this.state.formd || { recipients: [] };
    const recipients = row.recipients || [];
    const errors = this.state.errors;
    const recipientColumns = [
      { key: 'rn', header: '#', width: 40, render: (r, i) => i + 1 },
      { key: 'phone', header: 'Phone', render: (r, i) => <TextField id={`sms-phone-${i}`} label="" value={r.phone || r.msisdn || ''} onValueChange={(v) => this.updateRecipient(i, 'phone', v)} /> },
      { key: 'content', header: 'Content', render: (r, i) => <TextField id={`sms-content-${i}`} label="" value={r.content || ''} onValueChange={(v) => this.updateRecipient(i, 'content', v)} /> },
      { key: 'delete', header: strings.delete, align: 'center', render: (r, i) => <Checkbox checked={Boolean(r.delete)} onCheckedChange={(checked) => this.updateRecipient(i, 'delete', checked)} /> },
    ];

    return (
      <Sheet
        open={this.props.open}
        onClose={() => this.close()}
        title={this.props.title}
        size="xl"
        footer={<>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.close()}>{strings.close}</Button>
          <Button variant="primary" className="ios-btn--sm" onClick={() => this.submit()}>{strings.submit}</Button>
        </>}
      >
        <div className="ios-form">
          <TextArea id="sms-content-main" label="SMS Content" rows={4} value={row.content || ''} onValueChange={(v) => this.setField('content', v)} invalid={Boolean(errors.content)} />
          {errors.content ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{errors.content}</span> : null}
          <div style={{ color: 'var(--ios-text-secondary)', fontSize: 'var(--ios-fs-footnote)' }}>Count: <strong>{this.state.character_count}</strong></div>
          <Checkbox checked={Boolean(row.ismultiple)} onCheckedChange={(checked) => this.setField('ismultiple', checked)} label="Personalised SMS" />
          <DateField id="sms-send-time" label="Send Time" kind="datetime-local" value={dateTimeToNative(row.send_time)} onValueChange={(v) => this.setField('send_time', nativeToDateTime(v))} invalid={Boolean(errors.send_time)} />
          {errors.send_time ? <span style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{errors.send_time}</span> : null}
        </div>
        <h3 className="ios-section-title" style={{ marginTop: 'var(--ios-space-5)' }}>Phone Numbers</h3>
        <Toolbar>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.addRecipient()}>{strings.add_phone}</Button>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.removeLastRecipient()}>{strings.remove_phone}</Button>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.removeAllRecipients()}>{strings.remove_all_rows}</Button>
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.applyContentToRecipients()}>Apply Content</Button>
          <FileButton accept=".xls,.xlsx,.csv" onFiles={(files) => this.uploadRecipients(files)}>{strings.upload_phones_file}</FileButton>
        </Toolbar>
        {errors.recipients ? <p style={{ color: 'var(--ios-danger)', fontSize: 'var(--ios-fs-caption)' }}>{errors.recipients}</p> : null}
        <div style={{ marginTop: 'var(--ios-space-3)' }}>
          <Table columns={recipientColumns} rows={recipients} rowKey={(r, i) => r.id || r.phone || i} pageSize={50} emptyText="No recipients yet - add or import phone numbers." />
        </div>
      </Sheet>
    );
  }
}

class Download extends React.Component {
  render() {
    return (
      <ExcelFile
        filename="Sms_Log"
        ref={ref => this.excelRef = ref}
        element={
          <Button variant="ghost" className="ios-btn--sm" onClick={() => this.excelRef.download()}>
            <Icons.DownloadIcon size={16} />{strings.download}
          </Button>
        }>
        <ExcelSheet data={this.props.data} name="SMS">
          <ExcelColumn label="Created On" value="created_on" />
          <ExcelColumn label="Sent on" value="send_time" />
          <ExcelColumn label="Recipients" value="recipients_string" />
          <ExcelColumn label="Status" value="status" />
          <ExcelColumn label="Content" value="content" />
          <ExcelColumn label="Charges" value="charge" />
          <ExcelColumn label="Total Amount" value="total_amount" />
        </ExcelSheet>
      </ExcelFile>
    );
  }
}

const MerchantModuleSms = withRouter(MerchantModuleSmsC);

export default MerchantModuleSms;
