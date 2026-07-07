import React from 'react';
import { Messager } from 'rc-easyui';
import { withRouter } from "react-router-dom";
import MainMenuMerchant from "./MainMenuMerchant";
import common from "./Common";
import Progress from "./Progress";
import { MailIcon, MenuIcon, PaymentsIcon } from "./ShellIcons";
import Logo from "../media/images/gwlogo.png";

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
    } catch (Error) {
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
    const initials = (user.name || user.username || user.email || 'M').trim().substring(0, 1).toUpperCase();

    return (
      <div className="cpay-shell">
        <aside className="cpay-sidebar">
          <div className="cpay-brand">
            <img src={Logo} alt="CPay" className="cpay-brand-logo" />
            <div>
              <div className="cpay-brand-name">CPay</div>
              <div className="cpay-brand-product">Merchant Portal</div>
            </div>
          </div>
          <MainMenuMerchant activeItem={this.state.currentMenuKey} onChangeMenu={this.menuChanged} />
        </aside>

        <main className={`cpay-main ${this.state.currentMenuKey === 'dashboard' ? 'cpay-main-dashboard' : ''}`}>
          <header className="cpay-topbar">
            <div className="cpay-toolbar-left">
              <button className="cpay-icon-button" type="button" title="Navigation" aria-label="Navigation"><MenuIcon /></button>
              <button className="cpay-icon-button" type="button" title="Payments" aria-label="Payments"><PaymentsIcon /></button>
              <button className="cpay-icon-button" type="button" title="SMS" aria-label="SMS"><MailIcon /></button>
            </div>
            <div className="cpay-toolbar-right">
              <button className="cpay-secondary-button" type="button" onClick={() => this.goToScreen('settings')}>Settings</button>
              <button className="cpay-primary-button" type="button" onClick={() => window.location.reload()}>Refresh</button>
              <div className="cpay-user-chip" title={user.email || user.username || ''}>
                <span className="cpay-user-avatar">{initials}</span>
                <span className="cpay-user-meta">
                  <strong>{user.name || user.username || 'Merchant User'}</strong>
                  <span>{user.email || user.account_number || 'Signed in'}</span>
                </span>
              </div>
            </div>
          </header>

          <section className="cpay-page-heading">
            <div className="cpay-breadcrumb">Home <span>›</span> Merchant Portal <span>›</span> {current.title}</div>
            <h1>{current.title}</h1>
            <p>{current.subtitle}</p>
          </section>

          <section className="cpay-content">
            {this.state.currentMenuItem}
          </section>
          <footer className="cpay-footer">Copyright © 2019</footer>
        </main>

        <Messager ref={ref => this.messager = ref}></Messager>
        <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
      </div>
    );
  }
}

const LayoutMerchant = withRouter(LayoutMerchantWithOutRouter);
export default LayoutMerchant;
