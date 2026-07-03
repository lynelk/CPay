import React from 'react';
import { BrowserRouter as Router, Switch, Route } from 'react-router-dom';
import Login from './components/Login';
import LoginMerchant from './components/LoginMerchant';
import Layout from './components/Layout';
import LayoutMerchant from './components/LayoutMerchant';
import OperationsConsole from './features/OperationsConsole';

function Routers() {
  return (
    <Router>
      <Switch>
        <Route exact path="/">
          <LoginMerchant />
        </Route>
        <Route path="/portal">
          <Login />
        </Route>
        <Route path="/dashboard">
          <Layout />
        </Route>
        <Route path="/dashboardMerchant">
          <LayoutMerchant />
        </Route>
        <Route path="/operations">
          <OperationsConsole />
        </Route>
      </Switch>
    </Router>
  );
}

export default Routers;
