/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */
package org.netbeans.modules.db.explorer.dlg;

import org.netbeans.adito.db.OracleTableColumnHack;
import org.netbeans.lib.ddl.impl.*;
import org.netbeans.lib.ddl.util.CommandBuffer;

import java.util.*;

/**
 * This class factors out the DDL logic from the CreateTableDialog
 * 
 * @author <a href="mailto:david@vancouvering.com>David Van Couvering</a>
 */
public class CreateTableDDL {
    private Specification       spec;
    private String              schema;
    private String              tablename;

    public CreateTableDDL (
            Specification spec, 
            String schema,
            String tablename) {
        this.spec       = spec;
        this.schema     = schema;
        this.tablename  = _unEscapeName(tablename);
    }
    
    /**
     * Execute the DDL to create a table.  
     * 
     * @param columns - A Vector of ColumnItem representing the columns
     *      in the table
     * 
     * @param pkcols A Vector of ColumnItem representing the columns
     *      which are in the primary key for the table.  Can be null
     */
    public boolean execute(List<ColumnItem> columns, List<ColumnItem> pkcols) throws Exception
    {
      CommandBuffer cbuff = _getCommandBuffer(columns, pkcols, spec.getConnection().getDatabase());
      cbuff.execute();
      return cbuff.wasException();
    }

    public boolean execute(List<ColumnItem> columns, List<ColumnItem> pkcols, String pDbName) throws Exception
    {
      CommandBuffer cbuff = _getCommandBuffer(columns, pkcols, pDbName);
      cbuff.execute();
      return cbuff.wasException();
    }

    public String getDDL(List<ColumnItem> columns, List<ColumnItem> pkcols, String pDbName) throws Exception
    {
      return _getCommandBuffer(columns, pkcols, pDbName).getCommands();
    }

    private CommandBuffer _getCommandBuffer(List<ColumnItem> columns, List<ColumnItem> pkcols, String pDbName) throws Exception
    {
      CommandBuffer cbuff = new CommandBuffer();
      List<CreateIndex> idxCommands = new ArrayList<CreateIndex>();

      CreateTable cmd = spec.createCommandCreateTable(CaseConverter.convertCase(tablename, spec));

      cmd.setObjectOwner(schema);

          /* this variables and operation provide support for
           * creating indexes for primary or unique keys,
           * most of database are creating indexes by myself,
           * support was removed */
      org.netbeans.lib.ddl.impl.TableColumn cmdcol = null;
      CreateIndex xcmd = null;
      Iterator it = columns.iterator();
      while (it.hasNext()) {
        ColumnItem col = (ColumnItem)it.next();
        String name = CaseConverter.convertCase(col.getName(), spec);
        if (col.isPrimaryKey()&& !hasPrimaryKeys(pkcols))
        {
          if(isPkAllowed(col.getType().getName()))
          {
            cmdcol = cmd.createPrimaryKeyColumn(name);
            OracleTableColumnHack.fixPrimaryKeyColumn(spec, cmdcol, tablename, name, pDbName);
          }
        }
        else if (col.isUnique()&&!col.isPrimaryKey())
        {
          if(isIndexAllowed(col.getType().getName()))
          {
            cmdcol = cmd.createUniqueColumn(name);
            OracleTableColumnHack.fixUniqueColumn(spec, cmdcol, tablename, name, pDbName);
          }
        }
        else cmdcol = cmd.createColumn(name);

        //bugfix for #31064
        //combo.setSelectedItem(combo.getSelectedItem());

              cmdcol.setColumnType(Specification.getType(col.getType().getType()));
              cmdcol.setColumnSize(col.getSize());
              cmdcol.setDecimalSize(col.getScale());
              cmdcol.setNullAllowed(col.allowsNull());
              String defval = col.getDefaultValue();
              if (defval != null && defval.length() > 0)
                  cmdcol.setDefaultValue(defval);
              if (col.hasCheckConstraint()) {
                  // add the TABLE check constraint
                TableColumn checkConstraintCol = cmd.createCheckConstraint(name, col.getCheckConstraint());
                OracleTableColumnHack.fixCheckConstraint(spec, checkConstraintCol, tablename, name, pDbName);
              }
              if (col.isIndexed()&&!col.isPrimaryKey()&&!col.isUnique()) {
                  xcmd = spec.createCommandCreateIndex(tablename);
                  // This index is referring to a tablename that is being
                  // created now, versus an existing one.  This
                  // means we shouldn't quote it.
                  xcmd.setNewObject(true);
                  xcmd.setIndexName(_unEscapeName(OracleTableColumnHack.getValidName(tablename, name, "IDX"))); // NOI18N
                  xcmd.setIndexType(new String());
                  xcmd.setObjectOwner(schema);
                  xcmd.specifyNewColumn(name);
                  idxCommands.add(xcmd);
              }
          }
          if( hasPrimaryKeys(pkcols) ) {
              cmdcol = cmd.createPrimaryKeyConstraint(tablename);
              cmdcol.setTableConstraintColumns(new Vector(pkcols));
              cmdcol.setColumnType(0);
              cmdcol.setColumnSize(0);
              cmdcol.setDecimalSize(0);
              cmdcol.setNullAllowed(true);

      }
      cbuff.add(cmd);
      for(int i=0;i<idxCommands.size();i++)
        cbuff.add(idxCommands.get(i));

      return cbuff;
    }
    
    private boolean hasPrimaryKeys(List<ColumnItem> pkcols)
    {
      List<ColumnItem> newPkCols = new ArrayList<>();
      if(pkcols == null)
        return false;

      for (int i = 0; i < pkcols.size(); i++)
      {
        ColumnItem columnItem = pkcols.get(i);
        if(columnItem.isPrimaryKey())
          newPkCols.add(columnItem);
      }

      return newPkCols != null && newPkCols.size() > 0;
    }

    private String _unEscapeName(String pName)
    {
      return pName.replace("\"", "");
    }

    private boolean isPkAllowed(String pTypeName)
    {
      Vector<String> noPrimaryKeyTypes = (Vector<String>) spec.getProperties().get("NoPrimaryKeyTypes");
      if(noPrimaryKeyTypes != null && noPrimaryKeyTypes.contains(pTypeName))
        return false;

      return true;
    }

    private boolean isIndexAllowed(String pTypeName)
    {
      Vector<String> noIndexTypes = (Vector<String>) spec.getProperties().get("NoIndexTypes");
      if(noIndexTypes != null && noIndexTypes.contains(pTypeName))
        return false;

      return true;
    }

}
