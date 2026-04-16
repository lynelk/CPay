import React from 'react';
import { Messager, Menu, MenuItem, MenuSep, SubMenu } from 'rc-easyui';
import { useHistory, withRouter } from "react-router-dom";
import common from "./Common";
import Progress from "./Progress";
 
class MainMenuMerchantWithOutRouter extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      value: null,
      loader: false,
      progressValue: 0,
    }
  }
  handleItemClick(value) {
    this.props.onChangeMenu(value);
    this.setState({ value: value });
  }


  render() {
    return (
        <div>  
            <Menu inline onItemClick={this.handleItemClick.bind(this)}>
                <MenuItem value="dashboard" text="Dashboard" iconCls="icon-dashboard"></MenuItem>
                <MenuItem value="statement" text="Statement" iconCls="icon-report"></MenuItem>
                <MenuItem value="payments" text="Payments" iconCls="icon-banknote"></MenuItem>
                <MenuItem value="sms" text="SMS" iconCls="icon-iphone"></MenuItem>
                <MenuItem value="transactions" text="Transactions" iconCls="icon-report2"></MenuItem>
                <MenuItem value="admins" text="Administrators" iconCls="icon-man"></MenuItem>
                <MenuItem value="audittrail" text="Audit Trail" iconCls="icon-report"></MenuItem>
                {/*<MenuItem value="settings" text="Settings" iconCls="icon-settings"></MenuItem>*/}
                <MenuSep></MenuSep>
                <MenuItem value="exit" text="Logout" iconCls="icon-logout"></MenuItem>
            </Menu>
            <Messager ref={ref => this.messager = ref}></Messager>
            <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
        </div>
    );
  }
}

const MainMenuMerchant = withRouter(MainMenuMerchantWithOutRouter);
export default MainMenuMerchant;