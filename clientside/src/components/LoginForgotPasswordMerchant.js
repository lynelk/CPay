import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox } from 'rc-easyui';
import { Panel,Messager, Dialog } from 'rc-easyui';
import PropTypes from "prop-types";
import { useHistory, withRouter } from "react-router-dom";
import common from './Common';
import Progress from './Progress';

/*
* HANDLE FORGOT PASSWORD
*/
class ForgotPasswordMerchant extends React.Component{
    constructor(props) {
        super(props);
        this.state = {
          loader: false,
          progressValue: 0,
          user: {
              merchant_number: "",
              email:"",
              account_found: false,
          },
          account_found: false
      };
    }

    submit() {
        this.form.validate(errors => {
            if (errors !== null) {
              return;
            }
            let body = {
                email: this.state.user.email,
                merchant_number: this.state.user.merchant_number
            }
            this.setState({loader: true}, () => {
                fetch(common.base_url+"/auth/requestMerchantUserResetPassword", {
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
                                    //history.push("/dashboard");
                                    this.messager.alert({
                                        title: "Email Sent",
                                        icon: "info",
                                        msg: res.message,
                                        result: (r )=> {
                                            this.setState({
                                                account_found: true,
                                            });
                                        }
                                    });
                                } catch (ex) {
                                    this.messager.alert({
                                        title: "Error",
                                        icon: "error",
                                        msg: ex.message
                                    });
                                }
                            } else {
                                if (res.code) {
                                    this.messager.alert({title: "Error "+res.code,
                                        icon: "error",
                                        msg: res.message
                                    });
                                } else {
                                    this.messager.alert({title: "Error "+res.error,
                                        icon: "error",
                                        msg: res.message
                                    });
                                }
                            }
                        });
                    } catch(Error) {
                        this.setState({loader: false}, ()=> {
                            this.messager.alert({
                                title: "Error",
                                icon: "error",
                                msg: Error.message
                            });
                        });
                        return;
                    }
                }).catch((error) => {
                    this.setState({loader: false}, ()=> {
                        this.messager.alert({
                            title: "Error",
                            icon: "error",
                            msg: error.message
                        });
                    });
                });
            });
        });
    }

    handleChange(name, value) {
        let user = Object.assign({}, this.state.user);
        user[name] = value;
        this.setState({ user: user })
    }

    componentDidMount() {
        
    }

    shouldComponentUpdate(nextProps, nextState) {
        
        if (nextProps.merchantNumber != this.props.merchantNumber) {
            this.state.user.merchant_number = this.props.merchantNumber;
            this.setState({
                user: this.state.user
            }, () => {

            });
            return true;
        }
        return true;
    }

    componentWillUpdate() {
        
    }

    clearForm() {
        this.setState({
            user: {
                email: "",
                account_found: false,
                merchant_number: "",
            },
            account_found: false
        }, ()=> {
        });
    }

    closeThisDialog() {
        this.clearForm();
        this.dialog.close();
        this.props.onCloseDialog();
    }
  
    render() {
        const { user } = this.state;
        if (!this.props.showForgotPassword) {
            return (<div></div>);
        }

        return (
            
        <div>
          <Dialog 
            style={{width: 400}}
            ref={ref => this.dialog = ref}
            borderType="none" modal>
            <div style={{ padding: '0 20px' }}>
            <Form
                ref={ref => this.form = ref}
                style={{ maxWidth: 500, marginTop:5 }}
                model={user}
                labelWidth={120}
                labelAlign="right"
                rules={{
                        email: ["required"],
                        merchant_number: ['required']
                    }
                }
                onChange={this.handleChange.bind(this)}>

                    <h3>Confirm your Merchang Mumber and Email</h3>

                    <div className="mytext">
                        <p>Merchant Number</p>
                    </div>
                    <FormField name="merchant_number" label="">
                        <TextBox value={this.state.user.merchant_number}></TextBox>
                    </FormField>

                    <div className="mytext">
                        <p>Please enter your email address.</p>
                    </div>

                    <FormField name="email" label="">
                        <TextBox value={this.state.user.email}></TextBox>
                    </FormField>
                    
                </Form>
            </div>
            <div className="dialog-button">
                <LinkButton className="submit-button-red" onClick={this.submit.bind(this)} style={{ width: 80}}>Submit</LinkButton>
                <LinkButton onClick={() => {this.closeThisDialog()}} style={{ width: 80 }}>Cancel</LinkButton>
            </div>
          </Dialog>
          <ResetPasswordMerchantN 
            closeThisDialog={this.closeThisDialog.bind(this)}
            accountFound={this.state.account_found} 
            email={this.state.user.email}
            merchantNumber={this.state.user.merchant_number} />
          <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
          <Messager ref={ref => this.messager = ref}></Messager>
        </div>
      );
    }
}

class ResetPasswordMerchantN extends React.Component{
    constructor(props) {
        super(props);
        this.state = {
            user: {
                verification_code:"",
                new_password: "",
                confirm_password: ""
            },
            loader: false,
            progressValue: 0
        };
    }

    handleChange(name, value) {
        let user = Object.assign({}, this.state.user);
        user[name] = value;
        this.setState({ user: user })
    }

    componentWillReceiveProps() {
        //alert(JSON.stringify(this.props));
        if (!this.props.accountFound) {
            if (this.dialog != null) {
                this.dialog.open();
            }
        }
    }

    submit() {
        this.form.validate(errors => {
            if (errors !== null) {
              return;
            }

            if (this.state.user.new_password !== this.state.user.confirm_password) {
                this.messager.alert({
                    title: "Error",
                    icon: "error",
                    msg: "The new password does not match with confirm password."
                });
                return;
            }   

            let body = {
                email: this.props.email,
                verification_code: this.state.user.verification_code,
                new_password: this.state.user.new_password,
                merchant_number: this.props.merchantNumber,
            }
            this.setState({loader: true}, () => {
                fetch(common.base_url+"/auth/resetPasswordMerchant", {
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
                                    this.messager.alert({
                                        title: "Success!",
                                        icon: "info",
                                        msg: res.message,
                                        result: (r )=> {
                                            this.props.closeThisDialog();
                                            this.dialog.close();
                                        }
                                    });
                                } catch (ex) {
                                    this.messager.alert({
                                        title: "Error",
                                        icon: "error",
                                        msg: ex.message
                                    });
                                }
                            } else {
                                if (res.code) {
                                    this.messager.alert({title: "Error "+res.code,
                                        icon: "error",
                                        msg: res.message
                                    });
                                } else {
                                    this.messager.alert({title: "Error "+res.error,
                                        icon: "error",
                                        msg: res.message
                                    });
                                }
                            }
                        });
                    } catch(Error) {
                        this.setState({loader: false}, ()=> {
                            this.messager.alert({
                                title: "Error",
                                icon: "error",
                                msg: Error.message
                            });
                        });
                        return;
                    }
                }).catch((error) => {
                    this.setState({loader: false}, ()=> {
                        this.messager.alert({
                            title: "Error",
                            icon: "error",
                            msg: error.message
                        });
                    });
                });
            });
        });
    }

    render() {
        const { user } = this.state;
        if (!this.props.accountFound) {
            return (<div></div>);
        }

        return (
          <div>
            <Dialog 
                style={{width: 600}}
                ref={ref => this.dialog = ref}
                borderType="none" modal>
                <div style={{ padding: '20px 20px' }}>
                    <h2 align="center">Complete Password Reset</h2>
                    <div align="center">
                        <p>Provide here the verificaiton code we have sent to your email address.</p>
                    </div>
                    <Form
                        ref={ref => this.form = ref}
                        style={{ marginTop:5 }}
                        model={user}
                        labelWidth={200}
                        labelAlign="left"
                        rules={{
                                verification_code: ["required"],
                                new_password: ["required"],
                                confirm_password: ["required"]
                            }
                        }
                        onChange={this.handleChange.bind(this)}>
                            <FormField name="verification_code" label="Verification Code:">
                                <TextBox value={this.state.user.verification_code}></TextBox>
                            </FormField>

                            <FormField name="new_password" label="Password:">
                                <PasswordBox value={this.state.user.new_password}  placeholder="Password" iconCls="icon-lock"></PasswordBox>
                            </FormField>

                            <FormField name="confirm_password" label="Confirm new Password:">
                                <PasswordBox value={this.state.user.confirm_password}  placeholder="Confirm new Password" iconCls="icon-lock"></PasswordBox>
                            </FormField>
                    </Form>
                </div>
                <div className="dialog-button">
                    <LinkButton className="submit-button-red" onClick={this.submit.bind(this)} style={{ width: 80 }}>Submit</LinkButton>
                    <LinkButton onClick={() => {this.dialog.close()}} style={{ width: 80 }}>Cancel</LinkButton>
                </div>
            </Dialog>
            <Progress loaderState={this.state.loader} progressValue={this.state.progressValue} />
            <Messager ref={ref => this.messager = ref}></Messager>
          </div>
        );
    }
}


export default ForgotPasswordMerchant;