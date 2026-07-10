import React from 'react';
import Messager from './StableMessager';
import { withRouter } from '../shared/router/compat';
import MainMenuMerchant from "./MainMenuMerchant";
import common from "./Common";
import Progress from "./Progress";
import Logo from "../media/images/gwlogo.png";
import {
  Shell, Sidebar, Brand, TopBar, IconButton, UserChip, Page, PageHeader,
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

const menuTitles = {
  dashboard: { title: 'Dashboard', subtitle: 'Track balances, activity, and service status.' },
  channels: { title: 'Payment Channels', subtitle: 'Manage MTN, Airtel, and payment channel access.' },
  statement: { title: 'Statement', subtitle: 'Review merchant account movement and balances.' },
  payments: { title: 'Payments', subtitle: 'Create and monitor payment activity.' },
  sms: { title: 'SMS', subtitle: 'Send SMS and review SMS balance activity.' },
  transactions: { title: 'Transactions', subtitle: 'Review merchant payment and SMS transactions.' },
  admins: { title: 'Administrators', subtitle: 'Manage merchant portal users.' },
  audittrail: { title: 'Audit Trail', subtitle: 'Review merchant user activity.' },
  settings: { title: 'Settings', subtitle: 'Configure merchant overrides, IP access, limits, and SMS charges.' },
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
      user: localStorage.getItem("merchantUser") != null ? JSON.parse(localStorage.getItem("merchantUser")) : {},
      currentMenuItem: this.renderModule('dashboard'),
    };
    this.menuChanged = this.menuChanged.bind(this);
  }

  async componentDidMount() {
    this.chartRef = React.createRef();
    const isLoggedIn = await this.isLoggedIn();
    const { history } = this.props;
    if (!isLoggedIn) {
      this.setState({ isLogged: false });
      this.messager.alert({
        title: "Session Expired!",
        icon: "info",
        msg: "Your session expired",
        result: () => history.push("/")
      });
    } else {
      this.setState({ isLogged: true });
    }
  }

  renderModule(item) {
    const moduleProps = {
      sessionExpired: this.sessionExpired?.bind(this),
      logOut: this.logoutUser?.bind(this),
      loader: this.startOrStopLoader?.bind(this),
    };

    switch (item) {
      case 'channels': return <MerchantModulePaymentChannels {...moduleProps} />;
      case 'statement': return <MerchantModuleMerchantAccount {...moduleProps} />;
      case 'admins': return <MerchantModuleAdmins {...moduleProps} />;
      case 'payments': return <MerchantModulePayments {...moduleProps} />;
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
      title: "Session Expired!",
      icon: "info",
      msg: "Your session expired",
      result: () => history.push("/")
    });
  }

  async isLoggedIn() {
    try {
      await this.setState({ loader: true, progressValue: 0 });
      const response = await fetch(common.base_url + "/auth/isMerchantUserLoggedIn", {
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
      currentMenuItem: this.renderModule(item),
    });
  }

  startOrStopLoader(action) {
    this.setState({ loader: action === "START", progressValue: 0 });
  }

  logoutUser() {
    const { history } = this.props;
    this.setState({ loader: true }, () => {
      fetch(common.base_url + "/auth/logoutMerchantUser", {
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
          <Sidebar brand={<Brand logo={Logo} name="CPay" product="Merchant Portal" />}>
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
                <IconButton label="Payments"><Icons.PaymentsIcon size={20} /></IconButton>
                <IconButton label="SMS"><Icons.SmsIcon size={20} /></IconButton>
              </>
            }
            right={
              <>
                <ThemeToggle />
                <Button variant="ghost" className="ios-btn--sm" onClick={() => this.goToScreen('settings')}>Settings</Button>
                <Button variant="primary" className="ios-btn--sm" onClick={() => window.location.reload()}>Refresh</Button>
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
          <PageHeader
            breadcrumb={`Home › Merchant Portal › ${current.title}`}
            title={current.title}
            subtitle={current.subtitle}
          />
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
