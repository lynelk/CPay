import React from 'react';
import { Form, FormField, LinkButton, PasswordBox, TextBox } from 'rc-easyui';
import Messager from './StableMessager';
import { withRouter } from "react-router-dom";
import common from './Common';
import Progress from './Progress';
import ForgotPasswordMerchant from './LoginForgotPasswordMerchant';
import strings from './locale';
import AuthShell from './AuthShell';

class LoginMerchantWithOutRouter extends React.Component {
  forms = null;

  constructor() {
    super();
    this.state = {
      loader: false,
      form: null,
      showforgotPassword: false,
      user: {
        account_number: null,
        username: null,
        password: null,
        accept: true
      }
    };
    this.closeForgotPassword = this.closeForgotPassword.bind(this);
  }

  footer() {
    return 'Copyright (c) 2019';
  }

  handleChange(name, value) {
    let user = Object.assign({}, this.state.user);
    user[name] = value;
    this.setState({ user: user });
  }

  async componentDidMount() {
    const { history } = this.props;
    const url = new URL(window.location.href);
    const uiportal = url.searchParams.get("uiportal");
    if (uiportal === "portal") {
      history.push("/portal");
      return;
    }

    let is_logged_in = await this.isLoggedIn();
    if (is_logged_in) {
      history.push("/dashboardMerchant");
    }
    window.addEventListener("keyup", this.eventHandler);
  }

  componentWillUnmount() {
    window.removeEventListener("keyup", this.eventHandler);
  }

  eventHandler = (event) => {
    if (event.key === "Enter" || event.keyCode === 13) {
      this.handleSubmit();
    }
  }

  handleSubmit() {
    this.form.validate(errors => {
      if (errors !== null) {
        return;
      }
      const { history } = this.props;

      let body = {
        username: this.state.user.username,
        password: this.state.user.password,
        account_number: this.state.user.account_number
      };

      this.startLoader(() => {
        fetch(common.base_url + "/auth/authenticateMerchantUser", {
          method: 'POST',
          mode: 'cors',
          cache: 'no-cache',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json'
          },
          redirect: 'follow',
          referrer: 'no-referrer',
          body: JSON.stringify(body)
        }).then((response) => {
          return response.text();
        }).then((response_) => {
          let res;
          try {
            res = JSON.parse(response_);
            this.setState({ loader: false }, () => {
              if (res.code === "000") {
                try {
                  localStorage.setItem("merchantUser", JSON.stringify(res.user));
                  history.push("/dashboardMerchant");
                } catch (ex) {
                  this.messager.alert({ title: "Error", icon: "error", msg: ex.message });
                }
              } else {
                this.messager.alert({ title: "Error " + res.code, icon: "error", msg: res.message });
              }
            });
          } catch (Error) {
            this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
          }
        }).catch((error) => {
          this.messager.alert({ title: "Error", icon: "error", msg: error.message });
        });
      });
    });
  }

  async isLoggedIn() {
    try {
      let response = await fetch(common.base_url + "/auth/isMerchantUserLoggedIn",
        {
          method: 'POST',
          mode: 'cors',
          cache: 'no-cache',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          redirect: 'follow',
          referrer: 'no-referrer',
          body: JSON.stringify({})
        });
      let res = await response.json();
      if (res.code === "000") {
        return res.message === "true";
      }
      return false;
    } catch (Error) {
      return false;
    }
  }

  startLoader(afterStart) {
    this.setState({ progressValue: 0, loader: true }, () => { afterStart(); });
  }

  showForgotPassword() { this.setState({ showforgotPassword: true }); }
  closeForgotPassword() { this.setState({ showforgotPassword: false }); }

  render() {
    const { user } = this.state;
    const { history } = this.props;
    return (
      <AuthShell
        className="cpay-auth-merchant"
        title={strings.merchant_title}
        subtitle="Merchant access"
        asideTitle="Merchant workspace"
        asideCopy="Account access, balances, and activity in one place."
        footer={this.footer()}
      >
        <Form
          ref={ref => this.form = ref}
          className="cpay-auth-form cpay-auth-form-merchant"
          style={{ width: '100%' }}
          model={user}
          labelWidth={148}
          labelAlign="right"
          rules={{ username: ["required"], password: ["required"], account_number: ['required'] }}
          onChange={this.handleChange.bind(this)}>

          <FormField name="account_number" label="Merchant Account:">
            <TextBox ref={ref => this.accountNumberRef = ref} value={this.state.user.account_number} style={{ width: '100%' }}></TextBox>
          </FormField>

          <FormField name="username" label="Username:">
            <TextBox ref={ref => this.usernameRef = ref} value={this.state.user.username} style={{ width: '100%' }}></TextBox>
          </FormField>

          <FormField name="password" label="Password:">
            <PasswordBox ref={ref => this.passwordRef = ref} value={this.state.user.password} placeholder="Password" iconCls="icon-lock" style={{ width: '100%' }}></PasswordBox>
          </FormField>

          <div className="cpay-auth-actions">
            <LinkButton className="cpay-auth-submit" onClick={this.handleSubmit.bind(this)}>Submit</LinkButton>
            <LinkButton className="cpay-auth-link" onClick={() => { history.push('/signup'); }} iconCls="icon-add" plain>Create merchant account</LinkButton>
            <LinkButton className="cpay-auth-link" onClick={() => { this.showForgotPassword(); }} iconCls="icon-help" plain>Forgot my password?</LinkButton>
          </div>
          <Messager ref={ref => this.messager = ref}></Messager>
        </Form>
        <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
        <ForgotPasswordMerchant merchantNumber={this.state.user.account_number} onCloseDialog={this.closeForgotPassword} showForgotPassword={this.state.showforgotPassword} />
      </AuthShell>
    );
  }
}

const LoginMerchant = withRouter(LoginMerchantWithOutRouter);
export default LoginMerchant;
