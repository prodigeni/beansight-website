*{ Display a single content for an insight  }*
*{ @param insigth: the insight  }*
*{ @param characters: the number of characters to display  }*
%{ 
      if(_characters == 0 || _characters == null ) { _characters = 32 }
/}%
#{insightContainer insight:_insight, tag:'span'}
    <a href='@{Application.showInsight(_insight.uniqueId)}'>${_insight.content.abbreviate(_characters)}</a>
#{/insightContainer}

