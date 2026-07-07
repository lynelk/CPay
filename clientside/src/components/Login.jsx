import React from 'react';
import { Form, FormField, LinkButton, PasswordBox, TextBox } from 'rc-easyui';
import Messager from './StableMessager';
import { withRouter } from "react-router-dom";
import common from './Common';
import Progress from './Progress';
import ForgotPassword from './LoginForgotPassword';
import strings from './locale';
import AuthShell from './AuthShell';

class LoginWithOutRouter extends React.Component {
  forms = null;

  constructor() {
    super();
    this.state = {
      loader: false,
      form: null,
      showforgotPassword: false,
      user: {
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
    let is_logged_in = await this.isLoggedIn();
    const { history } = this.props;
    if (is_logged_in) {
      history.push("/dashboard");
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
        password: this.state.user.password
      };

      this.startLoader(() => {
        fetch(common.base_url + "/auth/authenticate", {
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
                  localStorage.setItem("user", JSON.stringify(res.user));
                  history.push("/dashboard");
                } catch (ex) {
                  this.messager.alert({
                    title: "Error",
                    icon: "error",
                    msg: ex.message
                  });
                }
              } else {
                this.messager.alert({
                  title: "Error " + res.code,
                  icon: "error",
                  msg: res.message
                });
              }
            });
          } catch (Error) {
            this.messager.alert({
              title: "Error",
              icon: "error",
              msg: Error.message
            });
          }
        }).catch((error) => {
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
      let response = await fetch(common.base_url + "/auth/isLoggedIn",
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
    this.setState({
      progressValue: 0,
      loader: true
    }, () => {
      afterStart();
    });
  }

  showForgotPassword() {
    this.setState({ showforgotPassword: true });
  }

  closeForgotPassword() {
    this.setState({ showforgotPassword: false });
  }

  render() {
    const { user } = this.state;

    return (
      <AuthShell
        className="cpay-auth-admin"
        title={strings.portal_title}
        subtitle="Administrator access"
        asideTitle="Admin workspace"
        asideCopy="Operations, configuration, and reporting in one place."
        footer={this.footer()}
      >
        <Form
          ref={ref => this.form = ref}
          className="cpay-auth-form"
          style={{ width: '100%' }}
          model={user}
          labelWidth={104}
          labelAlign="right"
          rules={{
            username: ["required"],
            password: ["required"]
          }}
          onChange={this.handleChange.bind(this)}>

          <FormField name="username" label="Username:">
            <TextBox ref={ref => this.usernameRef = ref} value={this.state.user.username} style={{ width: '100%' }}></TextBox>
          </FormField>

          <FormField name="password" label="Password:">
            <PasswordBox
              ref={ref => this.passwordRef = ref}
              value={this.state.user.password}
              placeholder="Password"
              iconCls="icon-lock"
              style={{ width: '100%' }}></PasswordBox>
          </FormField>

          <div className="cpay-auth-actions">
            <LinkButton className="cpay-auth-submit" onClick={this.handleSubmit.bind(this)}>Submit</LinkButton>
            <LinkButton
              className="cpay-auth-link"
              onClick={() => { this.showForgotPassword(); }}
              iconCls="icon-help"
              plain>Forgot my password?</LinkButton>
          </div>
          <Messager ref={ref => this.messager = ref}></Messager>
        </Form>
        <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
        <ForgotPassword onCloseDialog={this.closeForgotPassword} showForgotPassword={this.state.showforgotPassword} />
      </AuthShell>
    );
  }
}

const Login = withRouter(LoginWithOutRouter);

export default Login;
