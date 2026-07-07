import React from 'react';
import { Messager } from 'rc-easyui';
import { withRouter } from "react-router-dom";
import MainMenu from "./MainMenu";
import common from "./Common";
import Progress from "./Progress";
import { CalendarIcon, MailIcon, MenuIcon } from "./ShellIcons";
import Logo from "../media/images/gwlogo.png";

import ModuleDashboard from './modules/ModuleDashboard';
import ModuleAdmins from './modules/ModuleAdmins';
import ModuleSettings from './modules/ModuleSettings';
import ModuleAuditTrail from './modules/ModuleAuditTrail';
import ModuleMerchants from './modules/ModuleMerchants';
import ModuleTransactions from './modules/ModuleTransactions';

const menuTitles = {
  dashboard: { title: 'Dashboard', subtitle: 'Track balances, transactions, and operational health.' },
  merchants: { title: 'Merchants', subtitle: 'Manage merchant profiles, accounts, and access.' },
  transactions: { title: 'Transactions', subtitle: 'Review payments, callbacks, ledger movement, and exports.' },
  admins: { title: 'Administrators', subtitle: 'Manage portal users and permissions.' },
  audittrail: { title: 'Audit Trail', subtitle: 'Review administrator activity and system events.' },
  settings: { title: 'Settings', subtitle: 'Configure payment gateways, SMS, email, and application controls.' },
};

class LayoutWithOutRouter extends React.Component {
  constructor(props) {
    super(props);
    this.chartRef = React.createRef();

    this.state = {
      loader: false,
      isLogged: false,
      progressValue: 0,
      currentMenuKey: 'dashboard',
      user: localStorage.getItem("user") != null ? JSON.parse(localStorage.getItem("user")) : {},
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
        result: () => history.push("/portal")
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
      case 'admins': return <ModuleAdmins {...moduleProps} />;
      case 'merchants': return <ModuleMerchants {...moduleProps} />;
      case 'transactions': return <ModuleTransactions {...moduleProps} />;
      case 'audittrail': return <ModuleAuditTrail {...moduleProps} />;
      case 'settings': return <ModuleSettings {...moduleProps} />;
      case 'dashboard':
      default: return <ModuleDashboard {...moduleProps} />;
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
      const response = await fetch(common.base_url + "/auth/isLoggedIn", {
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

  logoutUser() {
    this.messager.confirm({
      title: "Confirm Logout",
      msg: "Are you sure you want to logout?",
      result: r => {
        if (r) {
          this.logoutSendRequest();
        }
      }
    });
  }

  logoutSendRequest() {
    const { history } = this.props;
    this.setState({ loader: true }, () => {
      fetch(common.base_url + "/auth/logout", {
        method: 'POST',
        mode: 'cors',
        cache: 'no-cache',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        redirect: 'follow',
        referrer: 'no-referrer',
        body: JSON.stringify({})
      }).then(response => response.text())
        .then(responseText => {
          let res;
          try {
            res = JSON.parse(responseText);
            this.setState({ loader: false, progressValue: 0 }, () => {
              if (res.code === "000") {
                history.push("/portal");
              } else {
                this.messager.alert({ title: "Error " + res.code, icon: "error", msg: res.message });
              }
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

  startOrStopLoader(operation) {
    this.setState({ loader: operation === "START" });
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
    const initials = (user.name || user.email || 'U').trim().substring(0, 1).toUpperCase();

    return (
      <div className="cpay-shell">
        <aside className="cpay-sidebar">
          <div className="cpay-brand">
            <img src={Logo} alt="CPay" className="cpay-brand-logo" />
            <div>
              <div className="cpay-brand-name">CPay</div>
              <div className="cpay-brand-product">Admin Portal</div>
            </div>
          </div>
          <MainMenu activeItem={this.state.currentMenuKey} onChangeMenu={this.menuChanged} />
        </aside>

        <main className={`cpay-main ${this.state.currentMenuKey === 'dashboard' ? 'cpay-main-dashboard' : ''}`}>
          <header className="cpay-topbar">
            <div className="cpay-toolbar-left">
              <button className="cpay-icon-button" type="button" title="Navigation" aria-label="Navigation"><MenuIcon /></button>
              <button className="cpay-icon-button" type="button" title="Calendar" aria-label="Calendar"><CalendarIcon /></button>
              <button className="cpay-icon-button" type="button" title="Messages" aria-label="Messages"><MailIcon /></button>
            </div>
            <div className="cpay-toolbar-right">
              <button className="cpay-secondary-button" type="button" onClick={() => this.goToScreen('settings')}>Settings</button>
              <button className="cpay-primary-button" type="button" onClick={() => window.location.reload()}>Refresh</button>
              <div className="cpay-user-chip" title={user.email || ''}>
                <span className="cpay-user-avatar">{initials}</span>
                <span className="cpay-user-meta">
                  <strong>{user.name || 'User'}</strong>
                  <span>{user.email || 'Signed in'}</span>
                </span>
              </div>
            </div>
          </header>

          <section className="cpay-page-heading">
            <div className="cpay-breadcrumb">Home <span>›</span> Admin Portal <span>›</span> {current.title}</div>
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

const LayoutWithR = withRouter(LayoutWithOutRouter);
export default LayoutWithR;
