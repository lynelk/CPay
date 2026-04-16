import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox } from 'rc-easyui';
import { Panel,Messager } from 'rc-easyui';
import PropTypes from "prop-types";
import { useHistory, withRouter } from "react-router-dom";
import common from './Common';
import Progress from './Progress';
import ForgotPasswordMerchant from './LoginForgotPasswordMerchant';
import ReactDOM from 'react-dom';
import Logo from "../media/images/gwlogo.png"
import strings from './locale';

class LoginMerchantWithOutRouter extends React.Component {
  forms = null;
  /*static propTypes = {
    match: PropTypes.object.isRequired,
    location: PropTypes.object.isRequired,
    history: PropTypes.object.isRequired
  };*/
  constructor() {
    super();
    this.state = {
      loader:false,
      form: null,
      showforgotPassword: false,
      user: {
        account_number: null,
        username: null,
        password: null,
        accept: true
      }
    }
    this.closeForgotPassword = this.closeForgotPassword.bind(this);
  }  

  footer () {
    //alert("This is called.");
    return (
      <div align="center" style={{ padding: 5, fontSize:13 }}>Copyright Text here</div>
    );
  }

  handleChange(name, value) {
    let user = Object.assign({}, this.state.user);
    user[name] = value;
    this.setState({ user: user })
  }

  async componentDidMount() {
    const { match, location, history } = this.props;
    var url = new URL(window.location.href);
    var uiportal = url.searchParams.get("uiportal");
    //alert(uiportal);
    if (uiportal == "portal") {
      history.push("/portal");
      return;
    }

    let is_logged_in = await this.isLoggedIn();
    console.log(is_logged_in);
    
    if (is_logged_in) {
      history.push("/dashboardMerchant");
    }
    ReactDOM.findDOMNode(this).addEventListener("keyup", this.eventHandler);
    ReactDOM.findDOMNode(this).addEventListener("keyup", this.eventHandler);
  }

  componentWillUnmount() {
    ReactDOM.findDOMNode(this).removeEventListener("keyup", this.eventHandler);
    ReactDOM.findDOMNode(this).removeEventListener("keyup", this.eventHandler);
  }

  eventHandler = (event) => {
    if (event.keyCode == 13) {
      this.handleSubmit();
    }
  }

  handleSubmit() {
    this.form.validate(errors => {
      if (errors !== null) {
        return;
      }
      console.log(this.props);
      const { match, location, history } = this.props;

      let body = {
        username: this.state.user.username, 
        password: this.state.user.password,
        account_number: this.state.user.account_number
      };

      //this.startLoader();

      this.startLoader( () => {
          fetch(common.base_url+"/auth/authenticateMerchantUser", {
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
                this.setState({loader: false}, ()=> {
                    if (res.code == "000") {
                        try {
                            localStorage.setItem("merchantUser", JSON.stringify(res.user));
                            history.push("/dashboardMerchant");
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

    });
  }


async isLoggedIn() {
    try {
        let response = await fetch(common.base_url+"/auth/isMerchantUserLoggedIn", 
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

  startLoader(afterStart) {
    this.setState({
      progressValue: 0,
      loader: true
    }, ()=> {
      afterStart();
    });
  }

  showForgotPassword() {
    this.setState({showforgotPassword:true});
  }

  closeForgotPassword() {
    this.setState({showforgotPassword:false});
  }

  render() {
    const { user } = this.state;
    const { match, location, history } = this.props;
    
    return (
      <div align="center" valign="center">
        <Panel 
            title={strings.merchant_title}
            bodyStyle={{ padding: 10 }} 
            style={{ height: 500, width: 600, marginTop: 60 }}
            footer={this.footer}>
            <Form
              ref={ref => this.form = ref}
              style={{ maxWidth: 500, marginTop:5 }}
              model={user}
              labelWidth={120}
              labelAlign="right"
              rules={{
                  username: ["required"],
                  password: ["required"],
                  account_number: ['required']
                }
              }
              onChange={this.handleChange.bind(this)}>

              <div align="center">
                <img src={Logo} style={{width:200}} />
              </div>

              <FormField name="account_number" label="Merchant Account Number:">
                  <TextBox ref={ref => this.accountNumberRef = ref} value={this.state.user.account_number}></TextBox>
              </FormField>
              
              <FormField name="username" label="Username:">
                <TextBox ref={ref => this.usernameRef = ref} value={this.state.user.username}></TextBox>
              </FormField>

              <FormField name="password" label="Password:">
                <PasswordBox 
                  ref={ref => this.passwordRef = ref} 
                  value={this.state.user.password}  
                  onKeyUp={(e)=>{alert("This is called")}}
                  placeholder="Password" iconCls="icon-lock"></PasswordBox>
              </FormField>

              <FormField style={{ marginLeft: 120 }}>
                <LinkButton onClick={this.handleSubmit.bind(this)}>Submit</LinkButton>
              </FormField>

              <FormField style={{ marginLeft: 120 }}>
                <LinkButton 
                  onClick={()=>{
                    this.showForgotPassword()
                  }} 
                  iconCls="icon-help" plain>Forgot my password?</LinkButton>
              </FormField>
              <Messager ref={ref => this.messager = ref}></Messager>
          </Form>
        </Panel>
        <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
        <ForgotPasswordMerchant 
          merchantNumber={this.state.user.account_number}
          onCloseDialog={this.closeForgotPassword} 
          showForgotPassword={this.state.showforgotPassword} />
    </div>
    );
  }
}

const LoginMerchant = withRouter(LoginMerchantWithOutRouter);

export default LoginMerchant;