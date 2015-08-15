/* Copyright */
package ucar.nc2.ft2.coverage;

import net.jcip.annotations.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.Attribute;
import ucar.nc2.AttributeContainer;
import ucar.nc2.AttributeContainerHelper;
import ucar.nc2.constants.AxisType;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.util.Indent;

import java.io.IOException;
import java.util.Formatter;
import java.util.List;

/**
 * Coverage CoordAxis abstract superclass
 *
 * @author caron
 * @since 7/11/2015
 */
@Immutable
abstract public class CoverageCoordAxis {
  static private final Logger logger = LoggerFactory.getLogger(CoverageCoordAxis.class);

  public enum Spacing {
    regular,                // regularly spaced points or intervals (start, end, npts), edges halfway between coords
    irregularPoint,         // irregular spaced points (values, npts), edges halfway between coords
    contiguousInterval,     // irregular contiguous spaced intervals (values, npts), values are the edges, and there are npts+1, coord halfway between edges
    discontiguousInterval   // irregular discontiguous spaced intervals (values, npts), values are the edges, and there are 2*npts: low0, high0, low1, high1..
  }


  public enum DependenceType {
    independent,             // has its own dimension, is a coordinate variable, eg x(x)
    dependent,               // aux coordinate, reftime(time) or time_bounds(time);
    scalar,                  // reftime
    twoD                     // time(reftime, time), lat(x,y)
  }

  protected final String name;
  protected final String units, description;
  protected final DataType dataType;
  protected final AxisType axisType;    // ucar.nc2.constants.AxisType ordinal
  protected final AttributeContainer attributes;
  protected final DependenceType dependenceType;
  protected final List<String> dependsOn;

  protected final int ncoords;            // number of coordinates (not values)
  protected final Spacing spacing;
  protected final double startValue;
  protected final double endValue;
  protected final double resolution;
  protected final CoordAxisReader reader;

  protected final TimeHelper timeHelper; // AxisType = Time, RunTime only
  private final boolean isSubset;

  // may be lazy eval
  protected double[] values;     // null if isRegular, CoordAxisReader for lazy eval

  protected CoverageCoordAxis(String name, String units, String description, DataType dataType, AxisType axisType, AttributeContainer atts,
                              DependenceType dependenceType, List<String> dependsOn, Spacing spacing, int ncoords, double startValue, double endValue, double resolution,
                              double[] values, CoordAxisReader reader, boolean isSubset) {
    this.name = name;
    this.units = units;
    this.description = description;
    this.dataType = dataType;
    this.axisType = axisType;
    this.attributes = atts;
    this.dependenceType = dependenceType;
    this.dependsOn = dependsOn;
    this.spacing = spacing;
    this.values = values;
    this.reader = reader; // used only if values == null

    if (values == null) {
      this.startValue = startValue;
      this.endValue = endValue;
    }  else {
      this.startValue = values[0];
      this.endValue = values[values.length-1];
      // could also check if regular, and change spacing
    }

    if (resolution == 0.0 && ncoords > 1)
      this.resolution = (endValue - startValue) / (ncoords - 1);
    else
      this.resolution = resolution;

    this.ncoords = ncoords;
    this.isSubset = isSubset;

    if (axisType == AxisType.Time || axisType == AxisType.RunTime)
      timeHelper = new TimeHelper(units, atts);
    else if (axisType == AxisType.TimeOffset)
      timeHelper = new TimeHelper(atts);
    else
      timeHelper = null;
  }

  // called after everything is wired in the dataset
  protected void setDataset(CoordSysContainer dataset) {
    // NOOP
  }

  // create a subset of this axis based on the SubsetParams. return this if no subset requested
  abstract public CoverageCoordAxis subset(SubsetParams params);

  // called only on dependent axes. pass in what if depends on
  abstract public CoverageCoordAxis subsetDependent(CoverageCoordAxis1D dependsOn);

  // called only on CoverageCoordAxis1D
  abstract public CoverageCoordAxis subset(double minValue, double maxValue);

  abstract public Array getCoordsAsArray() throws IOException;

  abstract public Array getCoordBoundsAsArray();

  public String getName() {
    return name;
  }

  public DataType getDataType() {
    return dataType;
  }

  public AxisType getAxisType() {
    return axisType;
  }

  public List<Attribute> getAttributes() {
    return attributes.getAttributes();
  }

  public AttributeContainer getAttributeContainer() {
    return new AttributeContainerHelper(name, attributes.getAttributes());
  }

  public int getNcoords() {
    return ncoords;
  }

  public Spacing getSpacing() {
    return spacing;
  }

  public boolean isRegular() {
    return (spacing == Spacing.regular);
  }

  public double getResolution() {
    return resolution;
  }

  public double getStartValue() {
    return startValue;
  }

  public double getEndValue() {
    return endValue;
  }

  public String getUnits() {
    return units;
  }

  public String getDescription() {
    return description;
  }

  public DependenceType getDependenceType() {
    return dependenceType;
  }

  public boolean isScalar() {
    return dependenceType == DependenceType.scalar;
  }

  public String getDependsOn() {
    StringBuilder sb = new StringBuilder();
    for (String name : dependsOn)
      sb.append(name).append(" ");
    return sb.toString();
  }

  public List<String> getDependsOnList() {
    return dependsOn;
  }

  public boolean getHasData() {
    return values != null;
  }

  public boolean isSubset() {
    return isSubset;
  }

  public boolean isTime2D() {
     return false;
   }

  public boolean isInterval() {
    return spacing == Spacing.contiguousInterval ||  spacing == Spacing.discontiguousInterval;
  }

   @Override
  public String toString() {
    Formatter f = new Formatter();
    Indent indent = new Indent(2);
    toString(f, indent);
    return f.toString();
  }

  public int[] getShape() {
    if (getDependenceType() == CoverageCoordAxis.DependenceType.scalar)
      return new int[0];
    return new int[] {ncoords};
  }

  public Range getRange() {
    if (getDependenceType() == CoverageCoordAxis.DependenceType.scalar)
      return Range.EMPTY;

    try {
      return new Range(name, 0, ncoords-1);
    } catch (InvalidRangeException e) {
      throw new RuntimeException(e);
    }
  }

  public void toString(Formatter f, Indent indent) {
    f.format("%sCoordAxis '%s' (%s)%n", indent, name, getClass().getName());
    f.format("%s  axisType=%s dataType=%s units='%s'", indent, axisType, dataType, units);
    if (timeHelper != null) f.format(" refDate=%s", timeHelper.getRefDate());
    f.format("%n");
    f.format("%s  npts: %d [%f,%f] spacing=%s", indent, ncoords, startValue, endValue, spacing);
    if (getResolution() != 0.0)
      f.format(" resolution=%f", resolution);
    f.format(" %s", getDependenceType());
    if (dependsOn.size() > 0) f.format(" :");
    for (String s : dependsOn)
      f.format(" %s", s);
    f.format("%n");

    if (values != null) {
      int n = values.length;
      switch (spacing) {
        case irregularPoint:
        case contiguousInterval:
          f.format("%s  contiguous values (%d)=", indent, n);
          for (double v : values)
            f.format("%f,", v);
          f.format("%n");
          break;

        case discontiguousInterval:
          f.format("%s  discontiguous values (%d)=", indent, n);
          for (int i = 0; i < n; i += 2)
            f.format("(%f,%f) ", values[i], values[i + 1]);
          f.format("%n");
          break;
      }
    }
  }

  ///////////////////////////////////////////////
  // time coords only

  public double convert(CalendarDate date) {
    return timeHelper.convert(date);
  }

  public CalendarDate makeDate(double value) {
    return timeHelper.makeDate(value);
  }

  public CalendarDateRange getDateRange() {
    return timeHelper.getDateRange(startValue, endValue);
  }

  public double getOffsetInTimeUnits(CalendarDate convertFrom, CalendarDate convertTo) {
    return timeHelper.getOffsetInTimeUnits(convertFrom, convertTo);
  }

  ///////////////////////////////////////////////

  // will return null when isRegular
  public double[] getValues() {
    synchronized (this) {
      if (values == null && !isRegular() && reader != null)
        try {
          values = reader.readValues(this);
        } catch (IOException e) {
          logger.error("Failed to read " + name, e);
        }
    }
    return values;
  }
}
