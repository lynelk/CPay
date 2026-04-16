import React from 'react';
import {
    BrowserRouter as Router,
    Switch,
    Route,
    Link,
    useHistory
  } from "react-router-dom";
  import Login from './components/Login';
  import LoginMerchant from './components/LoginMerchant';
  import Layout from './components/Layout';
  import LayoutMerchant  from './components/LayoutMerchant';

class Routers extends React.Component {
    constructor(props) {
        super(props);
        this.state = {}
    }

    GoToDashboard() {
        let history = useHistory();
        history.push("/dashboard");
    }
    
    render() {
        return (
            <Router>
                <div>
                    <Switch>
                        <Route exact path="/">
                            <HomeMerchant router={this}/>
                        </Route>
                        <Route path="/portal">
                            <HomePortal />
                        </Route>
                        <Route path="/dashboard">
                            <Layout />
                        </Route>
                        <Route path="/dashboardMerchant">
                            <LayoutMerchant />
                        </Route>
                    </Switch>
                </div>
            </Router>
        );
    }
}

// You can think of these components as "pages"
// in your app.

class HomePortal extends React.Component {
    constructor(props) {
        super(props);
        this.state = {}
    }
    render() {
        return (
            <div>
                <Login router={this.props.router} />
            </div>
        );
    }
}

class HomeMerchant extends React.Component {
    constructor(props) {
        super(props);
        this.state = {}
    }
    render() {
        return (
            <div>
                <LoginMerchant router={this.props.router} />
            </div>
        );
    }
}

class Dashboard extends React.Component {
    constructor(props) {
        super(props);
        this.state = {}
    }
    render() {
        return (
            <div>
                <ul>
                    <li>
                        <Link to="/">Home</Link>
                    </li>
                    <li>
                        <Link to="/dashboard">Dashboard</Link>
                    </li>
                    <li>
                        <Link to="/">Logout</Link>
                    </li>
                </ul>
            </div>
        );
    }
}
   
export default Routers;