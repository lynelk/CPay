import React from 'react';
import Messager from './StableMessager';
import { withRouter } from "react-router-dom";
import Progress from "./Progress";

const navGroups = [
  {
    title: 'Workspace',
    items: [
      { value: 'dashboard', text: 'Dashboard', iconCls: 'icon-dashboard' },
      { value: 'merchants', text: 'Merchants', iconCls: 'icon-man' },
      { value: 'transactions', text: 'Transactions', iconCls: 'icon-report2' },
    ],
  },
  {
    title: 'Administration',
    items: [
      { value: 'admins', text: 'Administrators', iconCls: 'icon-two-men' },
      { value: 'audittrail', text: 'Audit Trail', iconCls: 'icon-report' },
      { value: 'settings', text: 'Settings', iconCls: 'icon-settings' },
    ],
  },
];

class MainMenuWithOutRouter extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      value: props.activeItem || 'dashboard',
      loader: false,
      progressValue: 0,
    };
  }

  componentDidUpdate(prevProps) {
    if (prevProps.activeItem !== this.props.activeItem && this.props.activeItem) {
      this.setState({ value: this.props.activeItem });
    }
  }

  handleItemClick(value) {
    this.props.onChangeMenu(value);
    this.setState({ value });
  }

  renderNavItem(item) {
    const isActive = this.state.value === item.value;
    return (
      <button
        type="button"
        key={item.value}
        className={`cpay-nav-item${isActive ? ' cpay-nav-item-active' : ''}`}
        onClick={() => this.handleItemClick(item.value)}>
        <span className={`cpay-nav-icon ${item.iconCls}`} aria-hidden="true" />
        <span>{item.text}</span>
      </button>
    );
  }

  render() {
    return (
      <div className="cpay-nav-menu">
        {navGroups.map(group => (
          <div className="cpay-nav-group" key={group.title}>
            <div className="cpay-nav-group-title">{group.title}</div>
            {group.items.map(item => this.renderNavItem(item))}
          </div>
        ))}
        <div className="cpay-nav-group cpay-nav-group-bottom">
          <button
            type="button"
            className="cpay-nav-item cpay-nav-item-danger"
            onClick={() => this.handleItemClick('exit')}>
            <span className="cpay-nav-icon icon-logout" aria-hidden="true" />
            <span>Logout</span>
          </button>
        </div>
        <Messager ref={ref => this.messager = ref}></Messager>
        <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
      </div>
    );
  }
}

const MainMenu = withRouter(MainMenuWithOutRouter);
export default MainMenu;
