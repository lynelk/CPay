import React from 'react';
import Chart from 'chart.js';
class LinearChart extends React.Component {
    constructor(props) {
      super(props);
      this.chartRef = React.createRef();
    }
  
    componentDidUpdate() {
      this.createChart(this.props.data);
      //new Chart(this.chartRef.current, data);
      
      //console.log(this.myChart);
      this.myChart = null;
    }

    shouldComponentUpdate(nextProps, nextState) {
      return true;
    }
  
    componentDidMount() {
      this.createChart(this.props.data);
    }

    createChart(data) {
      this.myChart = new Chart(this.chartRef.current, data);
      /*if (this.myChart == null) {
        this.myChart = new Chart(this.chartRef.current, data);
        //console.log(data);
      } else {
        //this.myChart = new Chart(this.chartRef.current, data);
        //console.log(this.myChart.data.datasets);
        this.myChart.data = data;
        //console.log(this.myChart.data.datasets);
        //console.log(data);
        //this.myChart.update();
      }*/
    }
  
    render() {
      return <canvas ref={this.chartRef} />;
    }
  }

  export default LinearChart;