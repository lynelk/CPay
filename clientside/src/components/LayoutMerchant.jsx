import React from 'react';
import Messager from './StableMessager';
import strings from './locale';
import { withRouter } from '../shared/router/compat';
import MainMenuMerchant from "./MainMenuMerchant";
import Progress from "./Progress";
import Logo from "../media/images/gwlogo.png";
import {
  Shell, Sidebar, Brand, TopBar, IconButton, UserChip, Page,
  Button, ThemeToggle, Icons,
} from '../ui';

import MerchantModuleDashboard from './modules/merchant/MerchantModuleDashboard';
import MerchantModuleAdmins from './modules/merchant/MerchantModuleAdmins';
import MerchantModuleSettings from './modules/merchant/MerchantModuleSettings';
import MerchantModuleAuditTrail from './modules/merchant/MerchantModuleAuditTrail';
import MerchantModulePayments from './modules/merchant/MerchantModulePayments';
import MerchantModuleTransactions from './modules/merchant/MerchantModuleTransactions';
import MerchantModuleMerchantAccount from './modules/merchant/MerchantModuleMerchantsAccount';
import MerchantModuleCommunication from './modules/merchant/MerchantModuleCommunication';
import MerchantModulePaymentChannels from './modules/merchant/MerchantModulePaymentChannels';
import MerchantModuleWebhooks from './modules/merchant/MerchantModuleWebhooks';
import MerchantModuleKyc from './modules/merchant/MerchantModuleKyc';
import MerchantModuleBilling from './modules/merchant/MerchantModuleBilling';

import { apiFetch } from '../shared/api/httpClient';
import { apiUrl } from '../shared/config';
import { readStoredUser } from '../shared/useAuth';

const menuTitles = {
  dashboard: { title: strings.menu_dashboard, subtitle: strings.menu_dashboard_subtitle_merchant },
  channels: { title: strings.menu_channels, subtitle: strings.menu_channels_subtitle },
  statement: { title: 'Statements & Balances', subtitle: strings.menu_statement_subtitle },
  webhooks: { title: strings.menu_webhooks, subtitle: strings.menu_webhooks_subtitle },
  payments: { title: 'Payments', subtitle: strings.menu_payments_subtitle },
  sms: { title: 'Communication', subtitle: 'SMS, WhatsApp and USSD services for your account' },
  transactions: { title: strings.menu_transactions, subtitle: strings.menu_transactions_subtitle_merchant },
  kyc: { title: 'KYC & Customer Mgt', subtitle: 'Business verification, beneficial owners and KYC documents' },
  billing: { title: 'Billing', subtitle: 'Your current pricing and usage-to-date' },
  admins: { title: 'Team & Users', subtitle: strings.menu_admins_subtitle_merchant },
  audittrail: { title: strings.menu_audittrail, subtitle: strings.menu_audittrail_subtitle_merchant },
  settings: { title: strings.settings, subtitle: strings.menu_settings_subtitle_merchant },
};

const menuRoutes = {
  dashboard: '/dashboardMerchant/home',
  payments: '/dashboardMerchant/payments-transactions/payments',
  transactions: '/dashboardMerchant/payments-transactions/transactions',
  statement: '/dashboardMerchant/payments-transactions/statements',
  kyc: '/dashboardMerchant/kyc-customers/verification',
  billing: '/dashboardMerchant/billing/usage',
  sms: '/dashboardMerchant/communication',
  channels: '/dashboardMerchant/developers-integrations/payment-channels',
  webhooks: '/dashboardMerchant/developers-integrations/webhooks',
  admins: '/dashboardMerchant/administration/team',
  audittrail: '/dashboardMerchant/administration/audit',
  settings: '/dashboardMerchant/administration/settings',
};

const menuCapabilities = {
  dashboard: 'HOME',
  payments: 'PAYMENTS_TRANSACTIONS',
  transactions: 'PAYMENTS_TRANSACTIONS',
  statement: 'PAYMENTS_TRANSACTIONS',
  kyc: 'KYC_CUSTOMER_MGT',
  billing: 'BILLING',
  sms: 'COMMUNICATION',
  channels: 'DEVELOPERS_INTEGRATIONS',
  webhooks: 'DEVELOPERS_INTEGRATIONS',
  admins: 'ADMINISTRATION',
  audittrail: 'AUDIT',
  settings: 'ADMINISTRATION',
};

function menuForPath(pathname) {
  const exact = Object.entries(menuRoutes).find(([, path]) => pathname === path);
  if (exact) return exact[0];
  if (pathname === '/dashboardMerchant' || pathname === '/dashboardMerchant/') return 'dashboard';
  return 'dashboard';
}

function menuAllowed(item, capabilities) {
  const required = menuCapabilities[item];
  return Boolean(required && capabilities.includes(required));
}

class LayoutMerchantWithOutRouter extends React.Component {
  constructor(props) {
    super(props);
    this.chartRef = React.createRef();
    const initialKey = menuForPath(props.location?.pathname || '/dashboardMerchant');

    this.state = {
      loader: false,
      isLogged: false,
      progressValue: 0,
      currentMenuKey: initialKey,
      refreshTick: 0,
      user: readStoredUser('merchant'),
      role: null,
      capabilities: [],
      currentMenuItem: null,
    };
    this.menuChanged = this.menuChanged.bind(this);
    this.refreshCurrentPage = this.refreshCurrentPage.bind(this);
  }

  async componentDidMount() {
    this.chartRef = React.createRef();
    const access = await this.loadAuthenticatedAccess();
    const { history } = this.props;
    if (!access) {
      this.setState({ isLogged: false });
      this.messager.alert({
        title: strings.session_expired_title,
        icon: "info",
        msg: strings.session_expired_message,
        result: () => history.push("/")
      });
      return;
    }

    const stored = readStoredUser('merchant');
    const user = { ...stored, role: access.role, capabilities: access.capabilities };
    localStorage.setItem('merchantUser', JSON.stringify(user));

    const requestedKey = menuForPath(this.props.location?.pathname || '/dashboardMerchant');
    const key = menuAllowed(requestedKey, access.capabilities) ? requestedKey : 'dashboard';
    this.setState({
      isLogged: true,
      user,
      role: access.role,
      capabilities: access.capabilities,
      currentMenuKey: key,
      currentMenuItem: this.renderModule(key, this.state.refreshTick),
    });

    const pathname = this.props.location?.pathname || '';
    if (pathname === '/dashboardMerchant/' || pathname === '/dashboardMerchant' || key !== requestedKey) {
      history.replace(menuRoutes[key]);
    }
  }

  componentDidUpdate(prevProps) {
    if (prevProps.location?.pathname !== this.props.location?.pathname && this.state.isLogged) {
      const requestedKey = menuForPath(this.props.location?.pathname || '/dashboardMerchant');
      const key = menuAllowed(requestedKey, this.state.capabilities) ? requestedKey : 'dashboard';
      if (key !== requestedKey) {
        this.props.history.replace(menuRoutes[key]);
        return;
      }
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
      case 'channels': return <MerchantModulePaymentChannels {...moduleProps} />;
      case 'statement': return <MerchantModuleMerchantAccount {...moduleProps} />;
      case 'admins': return <MerchantModuleAdmins {...moduleProps} />;
      case 'payments': return <MerchantModulePayments {...moduleProps} />;
      case 'webhooks': return <MerchantModuleWebhooks {...moduleProps} />;
      case 'sms': return <MerchantModuleCommunication {...moduleProps} />;
      case 'transactions': return <MerchantModuleTransactions {...moduleProps} />;
      case 'kyc': return <MerchantModuleKyc {...moduleProps} />;
      case 'billing': return <MerchantModuleBilling {...moduleProps} />;
      case 'audittrail': return <MerchantModuleAuditTrail {...moduleProps} />;
      case 'settings': return <MerchantModuleSettings {...moduleProps} />;
      case 'dashboard':
      default: return <MerchantModuleDashboard {...moduleProps} />;
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

  async loadAuthenticatedAccess() {
    try {
      this.setState({ loader: true, progressValue: 0 });
      const sessionResponse = await apiFetch(apiUrl("/auth/isMerchantUserLoggedIn"), {
        method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer', body: JSON.stringify({})
      });
      const session = await sessionResponse.json();
      if (session.code !== '000' || session.message !== 'true') return false;

      const accessResponse = await apiFetch(apiUrl('/api/v2/merchant-self-service/access'), {
        method: 'GET', mode: 'cors', cache: 'no-cache', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer'
      });
      if (!accessResponse.ok) return false;
      const access = await accessResponse.json();
      if (access.code !== '000' || !Array.isArray(access.capabilities)) return false;
      return access;
    } catch {
      return false;
    } finally {
      this.setState({ loader: false, progressValue: 0 });
    }
  }

  menuChanged(item) { this.goToScreen(item); }

  goToScreen(item) {
    if (item === 'exit') { this.logoutUser(); return; }
    if (!menuAllowed(item, this.state.capabilities)) {
      this.props.history.replace(menuRoutes.dashboard);
      return;
    }
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

  startOrStopLoader(action) { this.setState({ loader: action === "START", progressValue: 0 }); }

  logoutUser() {
    const { history } = this.props;
    this.setState({ loader: true }, () => {
      apiFetch(apiUrl("/auth/logoutMerchantUser"), {
        method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
        headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer', body: JSON.stringify({})
      }).then(() => {
        localStorage.removeItem("merchantUser");
        history.push("/");
      }).catch(() => {
        localStorage.removeItem("merchantUser");
        history.push("/");
      });
    });
  }

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
    const canOpenSettings = this.state.capabilities.includes('ADMINISTRATION');

    return (
      <Shell
        navOpen={this.state.navOpen}
        sidebar={<Sidebar brand={<Brand logo={Logo} name="CPay" product="Merchant Portal" />}><MainMenuMerchant activeItem={this.state.currentMenuKey} onChangeMenu={this.menuChanged} capabilities={this.state.capabilities} /></Sidebar>}
        topbar={
          <TopBar
            left={<><IconButton label="Navigation" onClick={() => this.setState(s => ({ navOpen: !s.navOpen }))}><Icons.MenuIcon size={20} /></IconButton><div className="cpay-topbar-heading"><h1>{current.title}</h1><p>{current.subtitle}</p></div></>}
            right={<><ThemeToggle />{canOpenSettings ? <Button variant="ghost" className="ios-btn--sm" onClick={() => this.goToScreen('settings')}>{strings.settings}</Button> : null}<Button variant="primary" className="ios-btn--sm" onClick={this.refreshCurrentPage}>{strings.refresh}</Button><UserChip name={user.name || user.username || 'Merchant User'} meta={`${this.state.role || 'MERCHANT'} · ${user.email || user.account_number || 'Signed in'}`} /></>}
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

const LayoutMerchant = withRouter(LayoutMerchantWithOutRouter);
export default LayoutMerchant;
