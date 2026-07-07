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

  colorToRgba(color, alpha) {
    if (!color || !/^#[0-9a-f]{6}$/i.test(color)) {
      return `rgba(17, 152, 196, ${alpha})`;
    }

    const red = parseInt(color.slice(1, 3), 16);
    const green = parseInt(color.slice(3, 5), 16);
    const blue = parseInt(color.slice(5, 7), 16);
    return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
  }

  chartData(data) {
    const color = this.props.color || '#1198C4';
    const fillColor = this.colorToRgba(color, 0.14);
    const datasets = data && data.data && Array.isArray(data.data.datasets)
      ? data.data.datasets.map(dataset => ({
        borderColor: color,
        backgroundColor: fillColor,
        pointBackgroundColor: color,
        pointBorderColor: '#FFFFFF',
        tension: 0.35,
        ...dataset,
      }))
      : [];

    return {
      ...data.data,
      datasets,
    };
  }

  chartConfig(data) {
    if (!data) {
      return data;
    }

    return {
      ...data,
      data: this.chartData(data),
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
