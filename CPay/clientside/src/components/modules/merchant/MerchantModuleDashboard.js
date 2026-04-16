import React from 'react';
import { Form, FormField, TextBox, CheckBox, ComboBox, LinkButton, PasswordBox } from 'rc-easyui';
import { Panel, Layout, LayoutPanel, Messager } from 'rc-easyui';
import PropTypes from "prop-types";
import { useHistory, withRouter } from "react-router-dom";
import MainMenu from "../../MainMenu";
import common from "../../Common";
import Progress from "../../Progress";
import LinearChart from './LinearChart';
import styles from '../../styles';

class MerchantModuleDashboardC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            chartData: null, 
            chartDataTxTypes:null,
            chartDataTxVolumes:null,
            chartDataTxPerGateway: null
        };
    }

    componentDidMount() {
        this.getData("chartData", "getDashboardDetailsPayinsVsPayoutsMerchant");
        this.getData("chartDataTxTypes", "getDashboardDetailsTransactionTypesMerchant");
        this.getData("chartDataTxVolumes", "getDashboardDetailsTxVolumesMerchant");
        this.getData("chartDataTxPerGateway","getDashboardDetailsTxPerGatewayMerchant");
        //console.log(JSON.stringify(this.props));
    }

    getData(chartType, api) {
        this.props.loader("START");
        let searchData = {
            pageSize: this.state.pageSize,
            searchingValue: this.state.searchingValue,
            sort: 'asc'
        };
        fetch(common.base_url+"/transactions/"+api, {
            method: 'POST', // *GET, POST, PUT, DELETE, etc.
            mode: 'cors', // no-cors, *cors, same-origin
            cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
            credentials: 'include', // include, *same-origin, omit
            headers: {
                'Content-Type': 'application/json',
            },
            redirect: 'follow', // manual, *follow, error
            referrer: 'no-referrer', // no-referrer, *client
            body: JSON.stringify(searchData) // body data type must match "Content-Type" header
        }).then ((response)=>{
            return response.text();
        }).then((response_) => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code == "000") {
                    try {
                        switch(chartType) {
                            case "chartData":
                                this.setState({chartData: res.chartData});
                                break;
                            case "chartDataTxTypes":
                                this.setState({chartDataTxTypes: res.chartData});
                                break;
                            case "chartDataTxVolumes":
                                this.setState({chartDataTxVolumes: res.chartData});
                                break;
                            case "chartDataTxPerGateway":
                                this.setState({chartDataTxPerGateway: res.chartData});
                                break;
                            default:
                                break;
                        }
                    } catch (ex) {
                        this.messager.alert({
                            title: "Error",
                            icon: "error",
                            msg: ex.message
                        });
                    }
                } else {
                    //If session timed out
                    if (res.code == "107") {
                        this.sessionExpired();
                        return;
                    } else if (res.code == "110") {
                        this.accessNotAllowed(res.message);
                        return;
                    }

                    this.messager.alert({
                        title: "Error "+res.code,
                        icon: "error",
                        msg: res.message
                    });
                }
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
            this.props.loader("STOP");
            this.messager.alert({
                title: "Error",
                icon: "error",
                msg: error.message
            });
        });
    }

    sessionExpired() {
        const {history } = this.props;
        this.messager.alert({
            title: "Session Expired!",
            icon: "info",
            msg: "Your are session expired",
            result: (r) => {
                history.push("/portal");
            }
        });
    }

    render () {
        return (
            <div>
                <div>
                <Panel 
                    title="" 
                    bodyStyle={{ padding: 20 }} 
                    style={styles.dashboardChartPanel}>
                    <LinearChart
                        data={this.state.chartData}
                        title="Payins vs Payouts"
                        color="#70CAD1"
                    />
                    </Panel>
                    <Panel 
                    title="" 
                    bodyStyle={{ padding: 20 }} 
                    style={styles.dashboardChartPanel}>
                    <LinearChart
                        data={this.state.chartDataTxTypes}
                        title="Transaction Types"
                        color="#70CAD1"
                    />
                    </Panel>
                </div>
                <div>
                <Panel 
                    title="" 
                    bodyStyle={{ padding: 20 }} 
                    style={styles.dashboardChartPanel}>
                    <LinearChart
                        data={this.state.chartDataTxVolumes}
                        title="Amounts Volumes"
                        color="#70CAD1"
                        />
                    </Panel>
                    <Panel 
                    title="" 
                    bodyStyle={{ padding: 20 }} 
                    style={styles.dashboardChartPanel}>
                        <LinearChart
                            data={this.state.chartDataTxPerGateway}
                            title="TX Per Gateway"
                            color="#70CAD1"
                        />
                    </Panel>
                    <Messager ref={ref => this.messager = ref}></Messager>
                </div>
            </div>
        );
    }
}

const MerchantModuleDashboard = withRouter(MerchantModuleDashboardC);

export default MerchantModuleDashboard;