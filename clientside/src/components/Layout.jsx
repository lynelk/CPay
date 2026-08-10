import React from 'react';
import Messager from './StableMessager';
import strings from './locale';
import { withRouter } from '../shared/router/compat';
import MainMenu from "./MainMenu";
import Progress from "./Progress";
import Logo from "../media/images/gwlogo.png";
import {
  Shell, Sidebar, Brand, TopBar, IconButton, UserChip, Page,
  Button, ThemeToggle, Icons,
} from '../ui';

import ModuleDashboard from './modules/ModuleDashboard';
import ModuleAdmins from './modules/ModuleAdmins';
import ModuleSettings from './modules/ModuleSettings';
import ModuleAuditTrail from './modules/ModuleAuditTrail';
import ModuleMerchants from './modules/ModuleMerchants';
import ModuleTransactions from './modules/ModuleTransactions';
import ModuleReconciliation from './modules/ModuleReconciliation';
import ModuleFinanceClose from './modules/ModuleFinanceClose';
import ModulePayoutApprovals from './modules/ModulePayoutApprovals';
import ModulePayoutControls from './modules/ModulePayoutControls';
import ModuleSettlementClose from './modules/ModuleSettlementClose';
import ModuleWebhookOps from './modules/ModuleWebhookOps';
import ModuleCommunicationRouting from './modules/ModuleCommunicationRouting';
import ModuleKycCustomerManagement from './modules/ModuleKycCustomerManagement';
import ModuleBilling from './modules/ModuleBilling';

import { apiFetch } from '../shared/api/httpClient';
import { apiUrl } from '../shared/config';
import { readStoredUser } from '../shared/useAuth';

const menuTitles = {
  dashboard: { title: strings.menu_dashboard, subtitle: strings.menu_dashboard_subtitle_admin },
  merchants: { title: 'Customer Directory', subtitle: 'Customer and merchant account administration' },
  kyccustomers: { title: 'KYC & Customer Mgt', subtitle: 'KYC/KYB profiles, compliance cases and customer risk review' },
  billing: { title: 'Billing', subtitle: 'Pricing, rating and price-book administration' },
  transactions: { title: strings.menu_transactions, subtitle: strings.menu_transactions_subtitle_admin },
  reconciliation: { title: strings.menu_reconciliation, subtitle: strings.menu_reconciliation_subtitle },
  financeclose: { title: 'Finance Close', subtitle: 'Maker-checker daily close for reconciliation' },
  payoutapprovals: { title: 'Payout Approvals', subtitle: 'Maker-checker approval queue for limit-parked payouts' },
  payoutcontrols: { title: 'Payout Controls', subtitle: 'Configure payout risk limits enforced on the payment path' },
  settlementclose: { title: 'Settlement Close', subtitle: 'Maker-checker settlement batch close' },
  webhookops: { title: 'Webhook Operations', subtitle: 'Merchant callback verification, replay and test events' },
  communicationrouting: { title: 'Communication', subtitle: 'Communication provider selection and routing rules' },
  admins: { title: 'Users & Roles', subtitle: strings.menu_admins_subtitle_admin },
  audittrail: { title: strings.menu_audittrail, subtitle: strings.menu_audittrail_subtitle_admin },
  settings: { title: strings.settings, subtitle: strings.menu_settings_subtitle_admin },
};

const menuRoutes = {
  dashboard: '/dashboard/home',
  transactions: '/dashboard/payments-transactions/transactions',
  payoutapprovals: '/dashboard/payments-transactions/payouts/approvals',
  payoutcontrols: '/dashboard/payments-transactions/payouts/controls',
  reconciliation: '/dashboard/payments-transactions/reconciliation',
  financeclose: '/dashboard/payments-transactions/reconciliation/finance-close',
  settlementclose: '/dashboard/payments-transactions/settlements/close',
  merchants: '/dashboard/kyc-customers/directory',
  kyccustomers: '/dashboard/kyc-customers/compliance',
  billing: '/dashboard/billing/pricing',
  communicationrouting: '/dashboard/communication/routing',
  webhookops: '/dashboard/developers-integrations/webhooks',
  admins: '/dashboard/administration/users',
  audittrail: '/dashboard/administration/audit',
  settings: '/dashboard/administration/settings',
};

function menuForPath(pathname) {
  const exact = Object.entries(menuRoutes).find(([, path]) => pathname === path);
  if (exact) return exact[0];
  if (pathname === '/dashboard' || pathname === '/dashboard/') return 'dashboard';
  return 'dashboard';
}

class LayoutWithOutRouter extends React.Component {
  constructor(props) {
    super(props);
    this.chartRef = React.createRef();
    const initialKey = menuForPath(props.location?.pathname || '/dashboard');

    this.state = {
      loader: false,
      isLogged: false,
      progressValue: 0,
      currentMenuKey: initialKey,
      refreshTick: 0,
      user: readStoredUser('admin'),
      currentMenuItem: this.renderModule(initialKey, 0),
    };
    this.menuChanged = this.menuChanged.bind(this);
    this.refreshCurrentPage = this.refreshCurrentPage.bind(this);
  }

  async componentDidMount() {
    this.chartRef = React.createRef();
    const isLoggedIn = await this.isLoggedIn();
    const { history } = this.props;
    if (!isLoggedIn) {
      this.setState({ isLogged: false });
      this.messager.alert({
        title: strings.session_expired_title,
        icon: "info",
        msg: strings.session_expired_message,
        result: () => history.push("/portal")
      });
    } else {
      const key = menuForPath(this.props.location?.pathname || '/dashboard');
      this.setState({ isLogged: true, currentMenuKey: key, currentMenuItem: this.renderModule(key, this.state.refreshTick) });
      if ((this.props.location?.pathname || '') === '/dashboard/' || (this.props.location?.pathname || '') === '/dashboard') {
        history.replace(menuRoutes.dashboard);
      }
    }
  }

  componentDidUpdate(prevProps) {
    if (prevProps.location?.pathname !== this.props.location?.pathname) {
      const key = menuForPath(this.props.location?.pathname || '/dashboard');
      if (key !== this.state.currentMenuKey) {
        this.setState({ currentMenuKey: key, currentMenuItem: this.renderModule(key, this.state.refreshTick) });
      }
    }
  }

  renderModule(item, refreshSignal = this.state?.refreshTick || 0) {
    const moduleProps = {
      sessionExpired: this.sessionExpired?.bind(this),
      logOut: this.logoutUser?.bind(this),
      loader: this.startOrStopLoader?.bind(this),
      refreshSignal,
    };

    switch (item) {
      case 'admins': return <ModuleAdmins {...moduleProps} />;
      case 'merchants': return <ModuleMerchants {...moduleProps} />;
      case 'kyccustomers': return <ModuleKycCustomerManagement {...moduleProps} />;
      case 'billing': return <ModuleBilling {...moduleProps} />;
      case 'transactions': return <ModuleTransactions {...moduleProps} />;
      case 'reconciliation': return <ModuleReconciliation {...moduleProps} />;
      case 'financeclose': return <ModuleFinanceClose {...moduleProps} />;
      case 'payoutapprovals': return <ModulePayoutApprovals {...moduleProps} />;
      case 'payoutcontrols': return <ModulePayoutControls {...moduleProps} />;
      case 'settlementclose': return <ModuleSettlementClose {...moduleProps} />;
      case 'webhookops': return <ModuleWebhookOps {...moduleProps} />;
      case 'communicationrouting': return <ModuleCommunicationRouting {...moduleProps} />;
      case 'audittrail': return <ModuleAuditTrail {...moduleProps} />;
      case 'settings': return <ModuleSettings {...moduleProps} />;
      case 'dashboard':
      default: return <ModuleDashboard {...moduleProps} />;
    }
  }

  sessionExpired() {
    const { history } = this.props;
    this.messager.alert({
      title: strings.session_expired_title,
      icon: "info",
      msg: strings.session_expired_message,
      result: () => history.push("/")
    });
  }

  async isLoggedIn() {
    try {
      await this.setState({ loader: true, progressValue: 0 });
      const response = await apiFetch(apiUrl("/auth/isLoggedIn"), {
        method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer', body: JSON.stringify({})
      });
      await this.setState({ loader: false, progressValue: 0 });
      const res = await response.json();
      return res.code === "000" && res.message === "true";
    } catch {
      this.setState({ loader: false, progressValue: 0 });
      return false;
    }
  }

  menuChanged(item) { this.goToScreen(item); }

  goToScreen(item) {
    if (item === 'exit') { this.logoutUser(); return; }
    const route = menuRoutes[item];
    if (route && this.props.location?.pathname !== route) {
      this.props.history.push(route);
      return;
    }
    this.setState({ currentMenuKey: item, currentMenuItem: this.renderModule(item, this.state.refreshTick) });
  }

  refreshCurrentPage() {
    this.setState(prevState => {
      const refreshTick = prevState.refreshTick + 1;
      return { refreshTick, currentMenuItem: this.renderModule(prevState.currentMenuKey, refreshTick) };
    });
  }

  logoutUser() {
    this.messager.confirm({
      title: strings.confirm_logout_title, msg: strings.confirm_logout_message,
      result: r => { if (r) this.logoutSendRequest(); }
    });
  }

  logoutSendRequest() {
    const { history } = this.props;
    this.setState({ loader: true }, () => {
      apiFetch(apiUrl("/auth/logout"), {
        method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer', body: JSON.stringify({})
      }).then(response => response.text()).then(responseText => {
        let res;
        try {
          res = JSON.parse(responseText);
          this.setState({ loader: false, progressValue: 0 }, () => {
            if (res.code === "000") history.push("/portal");
            else this.messager.alert({ title: "Error " + res.code, icon: "error", msg: res.message });
          });
        } catch (Error) {
          this.setState({ loader: false, progressValue: 0 });
          this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
        }
      }).catch(error => {
        this.setState({ loader: false, progressValue: 0 });
        this.messager.alert({ title: "Error", icon: "error", msg: error.message });
      });
    });
  }

  startOrStopLoader(operation) { this.setState({ loader: operation === "START" }); }

  renderLoadingGate() {
    return (
      <div className="cpay-loading-gate">
        <canvas ref={this.chartRef} />
        <Messager ref={ref => this.messager = ref}></Messager>
        <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
      </div>
    );
  }

  render() {
    if (!this.state.isLogged) return this.renderLoadingGate();
    const user = this.state.user || {};
    const current = menuTitles[this.state.currentMenuKey] || menuTitles.dashboard;

    return (
      <Shell
        navOpen={this.state.navOpen}
        sidebar={<Sidebar brand={<Brand logo={Logo} name="CPay" product="Admin Portal" />}><MainMenu activeItem={this.state.currentMenuKey} onChangeMenu={this.menuChanged} /></Sidebar>}
        topbar={
          <TopBar
            left={<><IconButton label="Navigation" onClick={() => this.setState(s => ({ navOpen: !s.navOpen }))}><Icons.MenuIcon size={20} /></IconButton><div className="cpay-topbar-heading"><h1>{current.title}</h1><p>{current.subtitle}</p></div></>}
            right={<><ThemeToggle /><Button variant="ghost" className="ios-btn--sm" onClick={() => this.goToScreen('settings')}>{strings.settings}</Button><Button variant="primary" className="ios-btn--sm" onClick={this.refreshCurrentPage}>{strings.refresh}</Button><UserChip name={user.name || 'User'} meta={user.email || 'Signed in'} /></>}
          />
        }
      >
        <Page>{this.state.currentMenuItem}</Page>
        <Messager ref={ref => this.messager = ref}></Messager>
        <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
      </Shell>
    );
  }
}

const LayoutWithR = withRouter(LayoutWithOutRouter);
export default LayoutWithR;
