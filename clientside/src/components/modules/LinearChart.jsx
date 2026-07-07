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

  chartConfig(data) {
    if (!data) {
      return data;
    }

    return {
      ...data,
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: {
          mode: 'index',
          intersect: false,
          ...(data.options && data.options.interaction ? data.options.interaction : {})
        },
        plugins: {
          legend: {
            display: true,
            position: 'top',
            labels: {
              boxWidth: 10,
              boxHeight: 10,
              usePointStyle: true,
              font: { size: 11, weight: '600' }
            }
          },
          tooltip: { enabled: true },
          ...(data.options && data.options.plugins ? data.options.plugins : {})
        },
        scales: {
          ...(data.options && data.options.scales ? data.options.scales : {})
        },
        ...(data.options || {})
      }
    };
  }

  createChart(data) {
    this.destroyChart();

    if (!data || !this.chartRef.current) {
      return;
    }

    this.myChart = new Chart(this.chartRef.current, this.chartConfig(data));
  }

  render() {
    return <canvas ref={this.chartRef} />;
  }
}

export default LinearChart;
