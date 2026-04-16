import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox } from 'rc-easyui';
import { Panel, Layout, LayoutPanel, Messager } from 'rc-easyui';
import PropTypes from "prop-types";
import { useHistory, withRouter } from "react-router-dom";
import MainMenu from "./MainMenu";
import common from "./Common";
import Progress from "./Progress";
import styles from './styles';

import {
  BrowserRouter as Router,
  Switch,
  Route,
  Link,
} from "react-router-dom";

import ModuleDashboard from './modules/ModuleDashboard';
import ModuleAdmins from './modules/ModuleAdmins';
import ModuleSettings from './modules/ModuleSettings';
import ModuleAuditTrail from './modules/ModuleAuditTrail';
import ModuleMerchants from './modules/ModuleMerchants';
import ModuleTransactions from './modules/ModuleTransactions';

class LayoutWithOutRouter extends React.Component {
  constructor(props) {
    super(props);
    this.chartRef = React.createRef();

    this.state = {
      collapsed: false,
      loader: false,
      isLogged: false,
      progressValue:0,
      user: localStorage.getItem("user") != null ? JSON.parse(localStorage.getItem("user")) : {},
      data: this.getFeeds(),
      currentMenuItem: (<ModuleDashboard
        sessionExpired={this.sessionExpired.bind(this)}
        logOut={this.logoutUser.bind(this)}
        loader={this.startOrStopLoader.bind(this)}
      />),
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
    // Create random array of objects (with date)
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
    // Create random array of objects
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
      let response = await fetch(common.base_url+"/auth/isLoggedIn", 
      {
          method: 'POST', // *GET, POST, PUT, DELETE, etc.
          mode: 'cors', // no-cors, *cors, same-origin
          cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
          credentials: 'include', // include, *same-origin, omit
          headers: {
          'Content-Type': 'application/json'
          },
          redirect: 'follow', // manual, *follow, error
          referrer: 'no-referrer', // no-referrer, *client
          body: JSON.stringify({}) // body data type must match "Content-Type" header
      });

      await this.setState({loader:false, progressValue:0},() =>{});
      //console.log(await response.json());
      let res = await response.json();
      if (res.code == "000") {
        if (res.message == "true") {
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

  /*
  * Called whenever a menu item is clicked.
  * so we can change the content / load a module.
  * 
  * @Param String item: This the menu items clicked.
  */
  menuChanged(item) {
    this.goToScreen(item);
  }

  goToScreen(item) {
    switch(item) {
        case "exit":
          this.logoutUser();
          break;
        case "dashboard":
          this.setState({
            currentMenuItem: <ModuleDashboard 
              sessionExpired={this.sessionExpired.bind(this)}
              logOut={this.logoutUser.bind(this)}
              loader={this.startOrStopLoader.bind(this)} />
          });
          break;
        case "admins":
          this.setState({
            currentMenuItem: <ModuleAdmins 
              sessionExpired={this.sessionExpired.bind(this)}
              logOut={this.logoutUser.bind(this)}
              loader={this.startOrStopLoader.bind(this)} />
          });
          break;
        case "merchants":
            this.setState({
              currentMenuItem: <ModuleMerchants 
                sessionExpired={this.sessionExpired.bind(this)}
                logOut={this.logoutUser.bind(this)}
                loader={this.startOrStopLoader.bind(this)} />
            });
            break;//
        case "transactions":
            this.setState({
              currentMenuItem: <ModuleTransactions 
                sessionExpired={this.sessionExpired.bind(this)}
                logOut={this.logoutUser.bind(this)}
                loader={this.startOrStopLoader.bind(this)} />
            });
            break;
        case "audittrail":
          this.setState({
            currentMenuItem: <ModuleAuditTrail 
              sessionExpired={this.sessionExpired.bind(this)}
              logOut={this.logoutUser.bind(this)}
              loader={this.startOrStopLoader.bind(this)} />
          });
          break;
        case "settings":
          this.setState({
            currentMenuItem: <ModuleSettings 
                sessionExpired={this.sessionExpired.bind(this)}
                logOut={this.logoutUser.bind(this)}
                loader={this.startOrStopLoader.bind(this)} />
          });
          break;
      default:
          //alert("No values identified");
    }
  }

  logoutUser() {
    const { match, location, history } = this.props;
    this.messager.confirm({
        title: "Confirm to Logout",
        msg: "Are you sure you want to logout?",
        result: r => {
          if (r) {
            this.logoutSendRequest();
            //history.push("/");
          }
        }
    });
  }

  logoutSendRequest() {
    let body = {};
    const { match, location, history } = this.props;
    this.setState({
      loader: true
    }, () => {
        fetch(common.base_url+"/auth/logout", {
          method: 'POST', // *GET, POST, PUT, DELETE, etc.
          mode: 'cors', // no-cors, *cors, same-origin
          cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
          credentials: 'include', // include, *same-origin, omit
          headers: {
            'Content-Type': 'application/json',
          },
          redirect: 'follow', // manual, *follow, error
          referrer: 'no-referrer', // no-referrer, *client
          body: JSON.stringify(body) // body data type must match "Content-Type" header
        }).then ((response)=>{
          return response.text();
        }).then((response_) => {
          let res;
          try {
            res = JSON.parse(response_);
            this.setState({loader: false, progressValue:0}, ()=> {
              if (res.code == "000") {
                try {
                  history.push("/portal");
                } catch (ex) {
                  this.messager.alert({
                    title: "Error",
                    icon: "error",
                    msg: ex.message
                  });
                }
              } else {
                this.messager.alert({
                  title: "Error "+res.code,
                  icon: "error",
                  msg: res.message
                });
              }
            });
          } catch(Error) {
              //alert(Error.message);
              this.messager.alert({
                title: "Error",
                icon: "error",
                msg: Error.message
              });
              return;
          }
        }).catch((error) => {
          //alert(error.message);
          this.messager.alert({
            title: "Error",
            icon: "error",
            msg: error.message
          });
        });
    });
  }

  startOrStopLoader(operation) {
    if (operation == "START") {
      this.setState({loader:true});
    } else {
      this.setState({loader:false});
    }
  }

  render() {
    const titleStyle = {
      textAlign: 'center',
      marginTop: '10px'
    }
    if (!this.state.isLogged) {
      return (
        <div>
          <canvas ref={this.chartRef} />
          <Messager ref={ref => this.messager = ref}></Messager>
          <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
        </div>
      );
    }
    var u_profile;
    if (this.state.user != null) {
      u_profile = (<h3>{this.state.user.name}</h3>);
    } else {
      u_profile = <h3>No User Info</h3>
    }

    return (
      <div style={{height: window.innerHeight, wdith: window.innerWidth}}>
        <Layout style={{ width: '100%', height: '100%', border: '0' }}>
            <LayoutPanel title="Main Menu" 
              region="west" 
              collapsible={true} collapsed={this.state.collapsed} 
              expander={true} style={{ width: 205 }}>

            <MainMenu onChangeMenu={this.menuChanged} />

          </LayoutPanel>
          <LayoutPanel region="north" style={{ height: 50, border: '0' }}>
            <div style={titleStyle}>
              <Progress loaderState={this.state.loader} />
              <div className="mystyle-title-user-profile" align="right">
                Logged in as:  <span className="mystyle-title-user-profile-data ">{this.state.user.name}</span><br/>
                Email: <span className="mystyle-title-user-profile-data">{this.state.user.email}</span>
              </div>
            </div>
          </LayoutPanel>
          <LayoutPanel region="center" style={{ height: '100%', border: "0" }}>
            {this.state.currentMenuItem}
          </LayoutPanel>
          <LayoutPanel region="south" style={{ height: 50, border: '0' }}>
            <div style={titleStyle}>Copyright &copy; 2019</div>
          </LayoutPanel>
        </Layout>
        <Messager ref={ref => this.messager = ref}></Messager>
        <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
      </div>
    );
  }
}

const LayoutWithR = withRouter(LayoutWithOutRouter);

export default LayoutWithR;