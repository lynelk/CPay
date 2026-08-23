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
import MerchantModuleSms from './modules/merchant/MerchantModuleSms';
import MerchantModulePaymentChannels from './modules/merchant/MerchantModulePaymentChannels';
import MerchantModuleWebhooks from './modules/merchant/MerchantModuleWebhooks';
import MerchantModuleVending from './modules/merchant/MerchantModuleVending';
import MerchantModuleCitoServices from './modules/merchant/MerchantModuleCitoServices';

import { apiFetch } from '../shared/api/httpClient';
import { apiUrl } from '../shared/config';
import { readStoredUser } from '../shared/useAuth';

const menuTitles = {
  dashboard: { title: strings.menu_dashboard, subtitle: strings.menu_dashboard_subtitle_merchant },
  'cito-services': { title: 'Cito Services', subtitle: 'Entitlements, orchestration, marketplace, intelligence and platform tools' },
  channels: { title: strings.menu_channels, subtitle: strings.menu_channels_subtitle },
  statement: { title: strings.menu_statement, subtitle: strings.menu_statement_subtitle },
  webhooks: { title: strings.menu_webhooks, subtitle: strings.menu_webhooks_subtitle },
  payments: { title: strings.menu_payments, subtitle: strings.menu_payments_subtitle },
  vending: { title: 'Vending', subtitle: 'Devices, pricing, rentals, QR journeys and manufacturer integration' },
  sms: { title: strings.menu_sms, subtitle: strings.menu_sms_subtitle },
  transactions: { title: strings.menu_transactions, subtitle: strings.menu_transactions_subtitle_merchant },
  admins: { title: strings.menu_admins, subtitle: strings.menu_admins_subtitle_merchant },
  audittrail: { title: strings.menu_audittrail, subtitle: strings.menu_audittrail_subtitle_merchant },
  settings: { title: strings.settings, subtitle: strings.menu_settings_subtitle_merchant },
};

class LayoutMerchantWithOutRouter extends React.Component {
  constructor(props) {
    super(props);
    this.chartRef = React.createRef();

    this.state = {
      loader: false,
      isLogged: false,
      progressValue: 0,
      currentMenuKey: 'dashboard',
      refreshTick: 0,
      user: readStoredUser('merchant'),
      currentMenuItem: this.renderModule('dashboard', 0),
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
        result: () => history.push("/")
      });
    } else {
      this.setState({ isLogged: true });
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
      case 'cito-services': return <MerchantModuleCitoServices {...moduleProps} />;
      case 'channels': return <MerchantModulePaymentChannels {...moduleProps} />;
      case 'statement': return <MerchantModuleMerchantAccount {...moduleProps} />;
      case 'admins': return <MerchantModuleAdmins {...moduleProps} />;
      case 'payments': return <MerchantModulePayments {...moduleProps} />;
      case 'vending': return <MerchantModuleVending {...moduleProps} />;
      case 'webhooks': return <MerchantModuleWebhooks {...moduleProps} />;
      case 'sms': return <MerchantModuleSms {...moduleProps} />;
      case 'transactions': return <MerchantModuleTransactions {...moduleProps} />;
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

  async isLoggedIn() {
    try {
      await this.setState({ loader: true, progressValue: 0 });
      const response = await apiFetch(apiUrl("/auth/isMerchantUserLoggedIn"), {
        method: 'POST',
        mode: 'cors',
        cache: 'no-cache',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        redirect: 'follow',
        referrer: 'no-referrer',
        body: JSON.stringify({})
      });

      await this.setState({ loader: false, progressValue: 0 });
      const res = await response.json();
      return res.code === "000" && res.message === "true";
    } catch {
      this.setState({ loader: false, progressValue: 0 });
      return false;
    }
  }

  menuChanged(item) {
    this.goToScreen(item);
  }

  goToScreen(item) {
    if (item === 'exit') {
      this.logoutUser();
      return;
    }

    this.setState({
      currentMenuKey: item,
      currentMenuItem: this.renderModule(item, this.state.refreshTick),
    });
  }

  refreshCurrentPage() {
    this.setState(prevState => {
      const refreshTick = prevState.refreshTick + 1;
      return {
        refreshTick,
        currentMenuItem: this.renderModule(prevState.currentMenuKey, refreshTick),
      };
    });
  }

  startOrStopLoader(action) {
    this.setState({ loader: action === "START", progressValue: 0 });
  }

  logoutUser() {
    const { history } = this.props;
    this.setState({ loader: true }, () => {
      apiFetch(apiUrl("/auth/logoutMerchantUser"), {
        method: 'POST',
        mode: 'cors',
        cache: 'no-cache',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        redirect: 'follow',
        referrer: 'no-referrer',
        body: JSON.stringify({})
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
    if (!this.state.isLogged) {
      return this.renderLoadingGate();
    }

    const user = this.state.user || {};
    const current = menuTitles[this.state.currentMenuKey] || menuTitles.dashboard;

    return (
      <Shell
        navOpen={this.state.navOpen}
        sidebar={
          <Sidebar brand={<Brand logo={Logo} name="Cito" product="Merchant Workspace" />}>
            <MainMenuMerchant activeItem={this.state.currentMenuKey} onChangeMenu={this.menuChanged} />
          </Sidebar>
        }
        topbar={
          <TopBar
            left={
              <>
                <IconButton label="Navigation" onClick={() => this.setState(s => ({ navOpen: !s.navOpen }))}>
                  <Icons.MenuIcon size={20} />
                </IconButton>
                <div className="cpay-topbar-heading">
                  <h1>{current.title}</h1>
                  <p>{current.subtitle}</p>
                </div>
              </>
            }
            right={
              <>
                <ThemeToggle />
                <Button variant="ghost" className="ios-btn--sm" onClick={() => this.goToScreen('settings')}>{strings.settings}</Button>
                <Button variant="primary" className="ios-btn--sm" onClick={this.refreshCurrentPage}>{strings.refresh}</Button>
                <UserChip
                  name={user.name || user.username || 'Merchant User'}
                  meta={user.email || user.account_number || 'Signed in'}
                />
              </>
            }
          />
        }
      >
        <Page>
          {this.state.currentMenuItem}
        </Page>

        <Messager ref={ref => this.messager = ref}></Messager>
        <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
      </Shell>
    );
  }
}

const LayoutMerchant = withRouter(LayoutMerchantWithOutRouter);
export default LayoutMerchant;
