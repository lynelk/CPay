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

  cssVar(name, fallback) {
    if (typeof window === 'undefined' || !window.getComputedStyle) return fallback;
    const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return value || fallback;
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

    const fontFamily = this.cssVar('--ios-font', 'Inter, -apple-system, sans-serif');
    const gridColor = this.cssVar('--ios-separator', 'rgba(102,112,133,0.16)');
    const tickColor = this.cssVar('--ios-text-secondary', 'rgba(102,112,133,0.9)');
    const tooltipBg = this.cssVar('--ios-text', '#163B5C');
    const tooltipFg = this.cssVar('--ios-surface-solid', '#FFFFFF');
    const themedAxis = {
      grid: { color: gridColor, drawBorder: false },
      ticks: { color: tickColor, font: { family: fontFamily, size: 11 } },
    };

    return {
      ...data,
      data: this.chartData(data),
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        font: { family: fontFamily },
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
              color: tickColor,
              font: { family: fontFamily, size: 11, weight: '600' }
            }
          },
          tooltip: {
            enabled: true,
            backgroundColor: tooltipBg,
            titleColor: tooltipFg,
            bodyColor: tooltipFg,
            cornerRadius: 8,
            padding: 10,
            usePointStyle: true,
            titleFont: { family: fontFamily },
            bodyFont: { family: fontFamily },
          },
          ...(data.options && data.options.plugins ? data.options.plugins : {})
        },
        scales: {
          x: { ...themedAxis, ...(data.options && data.options.scales && data.options.scales.x ? data.options.scales.x : {}) },
          y: { ...themedAxis, ...(data.options && data.options.scales && data.options.scales.y ? data.options.scales.y : {}) },
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
