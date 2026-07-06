import React from 'react';
import Chart from 'chart.js/auto';

class LinearChart extends React.Component {
  constructor(props) {
    super(props);
    this.chartRef = React.createRef();
    this.myChart = null;
  }

  componentDidMount() {
    this.createChart(this.props.data);
  }

  componentDidUpdate(prevProps) {
    if (prevProps.data !== this.props.data) {
      this.createChart(this.props.data);
    }
  }

  componentWillUnmount() {
    this.destroyChart();
  }

  destroyChart() {
    if (this.myChart) {
      this.myChart.destroy();
      this.myChart = null;
    }
  }

  createChart(data) {
    this.destroyChart();

    if (!data || !this.chartRef.current) {
      return;
    }

    this.myChart = new Chart(this.chartRef.current, data);
  }

  render() {
    return <canvas ref={this.chartRef} />;
  }
}

export default LinearChart;
