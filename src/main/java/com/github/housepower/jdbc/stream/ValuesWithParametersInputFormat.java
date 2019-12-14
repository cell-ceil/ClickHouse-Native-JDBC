package com.github.housepower.jdbc.stream;

import com.github.housepower.jdbc.data.Block;
import com.github.housepower.jdbc.data.Column;
import com.github.housepower.jdbc.misc.SQLLexer;
import com.github.housepower.jdbc.misc.Slice;
import com.github.housepower.jdbc.misc.Validate;

import java.sql.SQLException;

public class ValuesWithParametersInputFormat implements InputFormat {

    private final Slice[] columns;
    private final int maxRows;

    private SQLLexer lexer;
    private Object[] parametersInLexer;
    private boolean parsed;
    private int currentRow;

    private final int pos;
    private final String query;

    public ValuesWithParametersInputFormat(String query, int pos, Slice[] columns,
                                           int maxRows)
        throws SQLException {
        this.pos = pos;
        this.query = query;
        this.columns = columns;
        this.currentRow = 0;
        this.maxRows = maxRows;
        this.lexer = new SQLLexer(pos, query);
        this.parsed = false;
    }

    private void parseLexer(Block header) throws SQLException {
        parametersInLexer = new Object[header.columns()];

        char nextChar = lexer.character();
        Validate.isTrue(nextChar == '(');
        for (int column = 0; column < header.columns(); column++) {
            if (column > 0) {
                Validate.isTrue(lexer.character() == ',');
            }

            if (!lexer.isCharacter('?')) {
                parametersInLexer[column] =
                    header.getByPosition(column).type().deserializeTextQuoted(lexer);
            } else {
                lexer.character();
            }
        }
        Validate.isTrue(lexer.character() == ')');
        parsed = true;
    }

    @Override
    public Block next(Block header, int blockMaxRows) throws SQLException {
        if (!parsed) {
            parseLexer(header);
        }

        int numRows = Math.min(blockMaxRows, maxRows - currentRow);
        Column[] cols = new Column[header.columns()];
        for (int i = 0; i < columns.length; i++) {
            if (parametersInLexer[i] == null) {
                cols[i] = new Column(header.getByPosition(i).name(),
                                     header.getByPosition(i).type(),
                                     columns[i].sub(currentRow, currentRow + numRows));
            } else {
                // set other parameters to const columns
                cols[i] = new Column(header.getByPosition(i).name(),
                                     header.getByPosition(i).type(),
                                     parametersInLexer[i], maxRows);
            }
        }
        currentRow += numRows;
        return new Block(numRows, cols);
    }
}
