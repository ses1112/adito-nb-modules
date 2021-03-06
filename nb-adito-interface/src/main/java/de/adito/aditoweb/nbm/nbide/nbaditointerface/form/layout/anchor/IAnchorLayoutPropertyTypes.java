package de.adito.aditoweb.nbm.nbide.nbaditointerface.form.layout.anchor;

import de.adito.aditoweb.nbm.nbide.nbaditointerface.form.layout.common.IAditoLayoutPropertyType;

import java.awt.*;

/**
 * Beschreibung der Properties f�r das AnchorLayout.
 *
 * @author J. Boesl, 11.05.11
 */
public interface IAnchorLayoutPropertyTypes
{

  IAditoLayoutPropertyType<Integer> x();

  IAditoLayoutPropertyType<Integer> y();

  IAditoLayoutPropertyType<Integer> width();

  IAditoLayoutPropertyType<Integer> height();

  IAditoLayoutPropertyType<Integer> dynamicHorizontal();

  IAditoLayoutPropertyType<Integer> dynamicVertical();

  IAditoLayoutPropertyType<Font> labelFont();

  IAditoLayoutPropertyType<String> labelText();

  IAditoLayoutPropertyType<Boolean> labelActive();

  IAditoLayoutPropertyType<Color> labelColor();

  IAditoLayoutPropertyType<Integer> labelAlignment();

  IAditoLayoutPropertyType<Integer> labelDistance();

  IAditoLayoutPropertyType<Integer> labelOrientation();

  IAditoLayoutPropertyType<Integer> labelAnchor();

  IAditoLayoutPropertyType<String> onDoubleClick();

}
