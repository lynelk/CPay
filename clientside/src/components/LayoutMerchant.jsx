import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox } from 'rc-easyui';
import { Panel, Layout, LayoutPanel, Messager } from 'rc-easyui';
import PropTypes from "prop-types";
import { useHistory, withRouter } from "react-router-dom";
import MainMenuMerchant from "./MainMenuMerchant";
import common from "./Common";
import Progress from "./Progress";
import styles from './styles';

import {
  BrowserRouter as Router,
  Switch,
  Route,
  Link,
} from "react-router-dom";

import MerchantModuleDashboard from './modules/merchant/MerchantModuleDashboard';
import MerchantModuleAdmins from './modules/merchant/MerchantModuleAdmins';
import MerchantModuleSettings from './modules/merchant/MerchantModuleSettings';
import MerchantModuleAuditTrail from './modules/merchant/MerchantModuleAuditTrail';
import MerchantModulePayments from './modules/merchant/MerchantModulePayments';
import MerchantModuleTransactions from './modules/merchant/MerchantModuleTransactions';
import MerchantModuleMerchantAccouunt from './modules/merchant/MerchantModuleMerchantsAccount';
import MerchantModuleSms from './modules/merchant/MerchantModuleSms';
import MerchantModulePaymentChannels from './modules/merchant/MerchantModulePaymentChannels';

import Logo from "../media/images/gwlogo.png";

class LayoutMerchantWithOutRouter extends React.Component {
    constructor(props) {
        super(props);
        this.chartRef = React.createRef();

        this.state = {
        collapsed: false,
        loader: false,
        isLogged: false,
        progressValue:0,
        user: localStorage.getItem("merchantUser") != null ? JSON.parse(localStorage.getItem("merchantUser")) : {},
        data: this.getFeeds(),
        currentMenuItem: (<MerchantModuleDashboard
            sessionExpired={this.sessionExpired.bind(this)}
            logOut={this.logoutUser.bind(this)}
            loader={this.startOrStopLoader.bind(this)}
        />),
        statementDialogStateOpened: false,
        }
        this.menuChanged = this.menuChanged.bind(this);
    }

    getFeeds() {
        let feeds = [];
    
        feeds.push({
        title: 'Visits',
        data: this.getRandomDateArray(150)
        });
    
        feeds.push({
        title: 'Categories',
        data: this.getRandomArray(20)
        });
    
        feeds.push({
        title: 'Categories',
        data: this.getRandomArray(10)
        });
    
        feeds.push({
        title: 'Data 4',
        data: this.getRandomArray(6)
        });
    
        return feeds;
    }

    getRandomDateArray(numItems) {
        let data = [];
        let baseTime = new Date('2018-05-01T00:00:00').getTime();
        let dayMs = 24 * 60 * 60 * 1000;
        for(var i = 0; i < numItems; i++) {
        data.push({
            time: new Date(baseTime + i * dayMs),
            value: Math.round(20 + 80 * Math.random())
        });
        }
        return data;
    }

    getRandomArray(numItems) {
        let names = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
        let data = [];
        for(var i = 0; i < numItems; i++) {
        data.push({
            label: names[i],
            value: Math.round(20 + 80 * Math.random())
        });
        }
        return data;
    }

    async componentDidMount() {
        this.chartRef = React.createRef();
        let is_logged_in = await this.isLoggedIn();
        console.log(is_logged_in);
        const { match, location, history } = this.props;
        if (!is_logged_in) {
            this.setState({isLogged:false}, () => {});
            this.messager.alert({
                title: "Session Expired!",
                icon: "info",
                msg: "Your are session expired",
                result: (r) => {
                history.push("/portal");
                }
            });
        } else {
            this.setState({isLogged:true}, () => {});
        }
    }


    sessionExpired() {
        const {history } = this.props;
        this.messager.alert({
            title: "Session Expired!",
            icon: "info",
            msg: "Your are session expired",
            result: (r) => {
                history.push("/");
            }
        });
    }

    async isLoggedIn() {
        try {
            await this.setState({loader:true, progressValue:0},() =>{});
            let response = await fetch(common.base_url+"/auth/isMerchantUserLoggedIn", 
            {
                method: 'POST',
                mode: 'cors',
                cache: 'no-cache',
                credentials: 'include',
                headers: {
                'Content-Type': 'application/json'
                },
                redirect: 'follow',
                referrer: 'no-referrer',
                body: JSON.stringify({})
            });

            await this.setState({loader:false, progressValue:0},() =>{});
            let res = await response.json();
            if (res.code === "000") {
                if (res.message === "true") {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } catch(Error) {
            return false;
        }
    }

    handleChange(name, value) {
        let user = Object.assign({}, this.state.user);
        user[name] = value;
        this.setState({ user: user })
    }

    menuChanged(item) {
        this.goToScreen(item);
    }

    goToScreen(item) {
      const { history } = this.props;
        switch(item) {
            case "exit":
            this.logoutUser();
            break;
            case "dashboard":
            this.setState({
                currentMenuItem: <MerchantModuleDashboard 
                sessionExpired={this.sessionExpired.bind(this)}
                logOut={this.logoutUser.bind(this)}
                loader={this.startOrStopLoader.bind(this)} />
            });
            break;
            case "channels":
            this.setState({
                currentMenuItem: <MerchantModulePaymentChannels
                sessionExpired={this.sessionExpired.bind(this)}
                logOut={this.logoutUser.bind(this)}
                loader={this.startOrStopLoader.bind(this)} />
            });
            break;
            case "statement":
            this.setState({
                currentMenuItem: <MerchantModuleMerchantAccouunt 
                sessionExpired={this.sessionExpired.bind(this)}
                logOut={this.logoutUser.bind(this)}
                loader={this.startOrStopLoader.bind(this)} />
            });
            break;
            case "admins":
            this.setState({
                currentMenuItem: <MerchantModuleAdmins 
                sessionExpired={this.sessionExpired.bind(this)}
                logOut={this.logoutUser.bind(this)}
                loader={this.startOrStopLoader.bind(this)} />
            });
            break;
            case "payments":
                this.setState({
                currentMenuItem: <MerchantModulePayments 
                    sessionExpired={this.sessionExpired.bind(this)}
                    logOut={this.logoutUser.bind(this)}
                    loader={this.startOrStopLoader.bind(this)} />
                });
                break;
            case "sms":
                this.setState({
                  currentMenuItem: <MerchantModuleSms 
                  sessionExpired={this.sessionExpired.bind(this)}
                  logOut={this.logoutUser.bind(this)}
                  loader={this.startOrStopLoader.bind(this)} />
                });
                break;
            case "transactions":
                this.setState({
                currentMenuItem: <MerchantModuleTransactions 
                    sessionExpired={this.sessionExpired.bind(this)}
                    logOut={this.logoutUser.bind(this)}
                    loader={this.startOrStopLoader.bind(this)} />
                });
                break;
            case "audittrail":
            this.setState({
                currentMenuItem: <MerchantModuleAuditTrail 
                sessionExpired={this.sessionExpired.bind(this)}
                logOut={this.logoutUser.bind(this)}
                loader={this.startOrStopLoader.bind(this)} />
            });
            break;
            case "settings":
            this.setState({
                currentMenuItem: <MerchantModuleSettings 
                    sessionExpired={this.sessionExpired.bind(this)}
                    logOut={this.logoutUser.bind(this)}
                    loader={this.startOrStopLoader.bind(this)} />
            });
            break;
            default:
        }
    }

    startOrStopLoader(action) {
        if (action === "START") {
            this.setState({loader:true, progressValue:0});
        } else {
            this.setState({loader:false, progressValue:0});
        }
    }

    logoutUser() {
        const { history } = this.props;
        fetch(common.base_url+"/auth/logoutMerchantUser", {
            method: 'POST',
            mode: 'cors',
            cache: 'no-cache',
            credentials: 'include',
            headers: {'Content-Type': 'application/json'},
            redirect: 'follow',
            referrer: 'no-referrer',
            body: JSON.stringify({})
        }).then(()=>{
            localStorage.removeItem("merchantUser");
            history.push("/");
        }).catch(()=>{
            localStorage.removeItem("merchantUser");
            history.push("/");
        });
    }

    render() {
        return (
            <div>
                <Layout style={{width:'100%',height:window.innerHeight}}>
                    <LayoutPanel region="west" split style={{width:220}}>
                        <div align="center"><img src={Logo} style={{width:150}} /></div>
                        <MainMenuMerchant onChangeMenu={this.menuChanged} />
                    </LayoutPanel>
                    <LayoutPanel region="center" style={{padding:5}}>
                        {this.state.currentMenuItem}
                    </LayoutPanel>
                </Layout>
                <Messager ref={ref => this.messager = ref}></Messager>
                <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
            </div>
        );
    }
}

const LayoutMerchant = withRouter(LayoutMerchantWithOutRouter);
export default LayoutMerchant;
