import React from 'react';
import Messager from './StableMessager';
import { withRouter } from '../shared/router/compat';
import MainMenu from "./MainMenu";
import common from "./Common";
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

import { apiFetch } from '../shared/api/httpClient';

const menuTitles = {
  dashboard: { title: 'Dashboard', subtitle: 'Track balances, transactions, and operational health.' },
  merchants: { title: 'Merchants', subtitle: 'Manage merchant profiles, accounts, and access.' },
  transactions: { title: 'Transactions', subtitle: 'Review payments, callbacks, ledger movement, and exports.' },
  reconciliation: { title: 'Reconciliation', subtitle: 'Match provider statement rows to CPay transactions.' },
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
      refreshTick: 0,
      user: localStorage.getItem("user") != null ? JSON.parse(localStorage.getItem("user")) : {},
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
        title: "Session Expired!",
        icon: "info",
        msg: "Your session expired",
        result: () => history.push("/portal")
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
      case 'admins': return <ModuleAdmins {...moduleProps} />;
      case 'merchants': return <ModuleMerchants {...moduleProps} />;
      case 'transactions': return <ModuleTransactions {...moduleProps} />;
      case 'reconciliation': return <ModuleReconciliation {...moduleProps} />;
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
      const response = await apiFetch(common.base_url + "/auth/isLoggedIn", {
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
      apiFetch(common.base_url + "/auth/logout", {
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

    return (
      <Shell
        navOpen={this.state.navOpen}
        sidebar={
          <Sidebar brand={<Brand logo={Logo} name="CPay" product="Admin Portal" />}>
            <MainMenu activeItem={this.state.currentMenuKey} onChangeMenu={this.menuChanged} />
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
                <Button variant="ghost" className="ios-btn--sm" onClick={() => this.goToScreen('settings')}>Settings</Button>
                <Button variant="primary" className="ios-btn--sm" onClick={this.refreshCurrentPage}>Refresh</Button>
                <UserChip name={user.name || 'User'} meta={user.email || 'Signed in'} />
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

const LayoutWithR = withRouter(LayoutWithOutRouter);
export default LayoutWithR;
